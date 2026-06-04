package com.beibu.mall.order.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.common.result.Result;
import com.beibu.mall.inventory.api.dto.StockOperationDTO;
import com.beibu.mall.inventory.api.feign.InventoryFeignClient;
import com.beibu.mall.order.api.dto.CreateOrderDTO;
import com.beibu.mall.order.api.dto.OrderVO;
import com.beibu.mall.order.config.SnowflakeIdGenerator;
import com.beibu.mall.order.entity.OrderInfo;
import com.beibu.mall.order.entity.OrderItem;
import com.beibu.mall.order.entity.OrderStatus;
import com.beibu.mall.order.mapper.OrderInfoMapper;
import com.beibu.mall.order.mapper.OrderItemMapper;
import com.beibu.mall.order.service.OrderService;
import com.beibu.mall.product.api.dto.SkuVO;
import com.beibu.mall.product.api.feign.ProductFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductFeignClient productFeignClient;
    private final InventoryFeignClient inventoryFeignClient;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final StringRedisTemplate stringRedisTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * 生成幂等键（基于请求内容哈希）
     *
     * 为什么基于请求内容而不是 orderNo？
     * orderNo 是服务端每次生成的，重复提交会产生不同的 orderNo，
     * 导致幂等检查失效。基于请求内容哈希，相同请求会生成相同的 key。
     *
     * I3修复：排序后拼接，相同商品不同顺序也生成相同key
     */
    private static String generateIdempotentKey(Long userId, CreateOrderDTO dto) {
        String content = userId + ":" + dto.getItems().stream()
                .sorted(Comparator.comparing(CreateOrderDTO.OrderItemDTO::getSkuId))
                .map(i -> i.getSkuId() + ":" + i.getQuantity())
                .collect(Collectors.joining(","));
        return "order:idempotent:" + DigestUtil.md5Hex(content);
    }

    /**
     * 创建订单
     *
     * @GlobalTransactional 替代 @Transactional，开启全局事务
     * - name: 事务名称（用于监控和日志，方便在 Seata 管理界面查找）
     * - rollbackFor: 哪些异常需要回滚（Exception.class 表示所有异常）
     * - timeoutMills: 15秒超时（15000ms），避免长时间持锁
     * - lockRetryInterval: 30ms 重试间隔
     * - lockRetryTimes: 3次重试，快速失败让客户端重试
     *
     * 这样"算价、预占库存、生成订单"就绑成一个全局事务了
     * 如果任何一步失败，Seata 会自动回滚所有已执行的操作
     */
    @Override
    @GlobalTransactional(
        name = "create-order", 
        rollbackFor = Exception.class,
        timeoutMills = 15000,
        lockRetryInterval = 30,
        lockRetryTimes = 3
    )
    public OrderVO createOrder(CreateOrderDTO createOrderDTO, Long userId) {
        log.info("开始创建订单，用户ID：{}，商品数量：{}", userId, createOrderDTO.getItems().size());

        // 新C2: 幂等键基于请求内容哈希，而非 orderNo
        String idempotentKey = generateIdempotentKey(userId, createOrderDTO);
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", 10, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(acquired)) {
            throw new BizException(40010, "请勿重复提交订单");
        }

        try {
            return doCreateOrder(createOrderDTO, userId);
        } catch (Exception e) {
            // MEDIUM-2: 下单失败时清理幂等键，允许用户重试
            stringRedisTemplate.delete(idempotentKey);
            throw e;
        }
    }

    /**
     * 实际创建订单的内部方法
     *
     * 为什么抽成单独方法？
     * 为了在 createOrder() 中统一处理幂等键的清理逻辑
     */
    private OrderVO doCreateOrder(CreateOrderDTO createOrderDTO, Long userId) {
        String orderNo = snowflakeIdGenerator.nextOrderNo();

        // ========== 1. 查询商品价格（RPC，事务内但只读） ==========
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CreateOrderDTO.OrderItemDTO itemDTO : createOrderDTO.getItems()) {
            Result<SkuVO> skuResult = productFeignClient.getSkuById(itemDTO.getSkuId());
            if (skuResult == null || skuResult.getCode() != 200 || skuResult.getData() == null) {
                throw new BizException(40001, "商品不存在或已下架，SKU ID：" + itemDTO.getSkuId());
            }

            SkuVO sku = skuResult.getData();
            if (sku.getStatus() == null || sku.getStatus() != 1) {
                throw new BizException(40002, "商品已下架：" + sku.getSpec());
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setSkuId(itemDTO.getSkuId());
            orderItem.setSpuId(sku.getSpuId());
            orderItem.setProductName(sku.getSpec());
            orderItem.setSkuSpec(sku.getSpec());
            orderItem.setProductImg(sku.getImg());
            orderItem.setPrice(sku.getPrice());
            orderItem.setQuantity(itemDTO.getQuantity());
            orderItem.setSubtotal(sku.getPrice().multiply(BigDecimal.valueOf(itemDTO.getQuantity())));

            orderItems.add(orderItem);
            totalAmount = totalAmount.add(orderItem.getSubtotal());
        }

        // ========== 2. 预占库存（RPC） ==========
        // 使用 Seata 分布式事务后，不再需要手动 catch 和回滚
        // 如果库存预占失败，Seata 会自动回滚整个事务（包括前面的查询）
        for (OrderItem item : orderItems) {
            StockOperationDTO stockDTO = new StockOperationDTO();
            stockDTO.setSkuId(String.valueOf(item.getSkuId()));
            stockDTO.setQuantity(item.getQuantity());
            stockDTO.setOrderId(orderNo);

            Result<Void> stockResult = inventoryFeignClient.occupyStock(stockDTO);
            if (stockResult == null || stockResult.getCode() != 200) {
                String msg = stockResult != null ? stockResult.getMsg() : "库存服务异常";
                throw new BizException(40003, "库存不足：" + msg);
            }
        }

        // ========== 3. 保存订单和明细（DB 操作，与上面同一个事务） ==========
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(snowflakeIdGenerator.nextId());
        orderInfo.setOrderNo(orderNo);
        orderInfo.setUserId(userId);
        orderInfo.setTotalAmount(totalAmount);
        orderInfo.setPayAmount(totalAmount);
        orderInfo.setFreightAmount(BigDecimal.ZERO);
        orderInfo.setDiscountAmount(BigDecimal.ZERO);
        orderInfo.setReceiverName("待补充");
        orderInfo.setReceiverPhone("待补充");
        orderInfo.setReceiverDetail("待补充地址");
        orderInfo.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        orderInfo.setAutoCancelTime(LocalDateTime.now().plusMinutes(30));
        orderInfo.setRemark(createOrderDTO.getRemark());

        orderInfoMapper.insert(orderInfo);

        for (OrderItem item : orderItems) {
            item.setId(snowflakeIdGenerator.nextId());
            item.setOrderId(orderInfo.getId());
            item.setOrderNo(orderNo);
            orderItemMapper.insert(item);
        }

        log.info("订单创建成功，订单号：{}，总金额：{}", orderNo, totalAmount);
        return buildOrderVO(orderInfo, orderItems);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderVO> listMyOrders(Long userId, int page, int size) {
        Page<OrderInfo> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getUserId, userId)
               .orderByDesc(OrderInfo::getCreateTime);

        Page<OrderInfo> orderPage = orderInfoMapper.selectPage(pageParam, wrapper);

        List<Long> orderIds = orderPage.getRecords().stream()
                .map(OrderInfo::getId)
                .collect(Collectors.toList());

        Map<Long, List<OrderItem>> itemsMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            List<OrderItem> allItems = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>()
                            .in(OrderItem::getOrderId, orderIds));
            itemsMap = allItems.stream()
                    .collect(Collectors.groupingBy(OrderItem::getOrderId));
        }

        Map<Long, List<OrderItem>> finalItemsMap = itemsMap;
        List<OrderVO> voList = orderPage.getRecords().stream()
                .map(orderInfo -> buildOrderVO(orderInfo,
                        finalItemsMap.getOrDefault(orderInfo.getId(), Collections.emptyList())))
                .collect(Collectors.toList());

        Page<OrderVO> voPage = new Page<>(page, size, orderPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderVO getOrderDetail(Long orderId, Long userId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo == null) {
            throw new BizException(40004, "订单不存在");
        }
        if (!orderInfo.getUserId().equals(userId)) {
            throw new BizException(40005, "无权访问该订单");
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        return buildOrderVO(orderInfo, items);
    }

    @Override
    public void cancelOrder(Long orderId, Long userId, String reason) {
        // 1. 编程式事务：只做 DB 操作，不持有连接做 RPC
        OrderInfo orderInfo = transactionTemplate.execute(status -> {
            OrderInfo info = orderInfoMapper.selectById(orderId);
            if (info == null) {
                throw new BizException(40004, "订单不存在");
            }
            if (!info.getUserId().equals(userId)) {
                throw new BizException(40005, "无权操作该订单");
            }

            Integer statusCode = info.getStatus();
            if (statusCode == null) {
                throw new BizException(40008, "订单状态异常");
            }
            OrderStatus orderStatus = OrderStatus.fromCode(statusCode);
            if (orderStatus == null || !orderStatus.canCancel()) {
                throw new BizException(40006, "当前订单状态不允许取消");
            }

            info.setStatus(OrderStatus.CANCELLED.getCode());
            info.setCancelTime(LocalDateTime.now());
            info.setCancelReason(StrUtil.isBlank(reason) ? "用户主动取消" : reason);
            orderInfoMapper.updateById(info);

            log.info("订单取消成功（DB），订单号：{}", info.getOrderNo());
            return info;
        });

        // 2. 事务外：释放库存（不持有 DB 连接）
        // 即使库存释放失败，订单已取消，补偿任务会重试
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderInfo.getId()));

        boolean allReleased = true;
        for (OrderItem item : items) {
            try {
                StockOperationDTO stockDTO = new StockOperationDTO();
                stockDTO.setSkuId(String.valueOf(item.getSkuId()));
                stockDTO.setQuantity(item.getQuantity());
                stockDTO.setOrderId(orderInfo.getOrderNo());

                Result<Void> stockResult = inventoryFeignClient.releaseStock(stockDTO);
                if (stockResult == null || stockResult.getCode() != 200) {
                    String msg = stockResult != null ? stockResult.getMsg() : "库存服务异常";
                    log.warn("释放库存失败，SKU：{}，订单号：{}，原因：{}，等待补偿任务处理",
                            item.getSkuId(), orderInfo.getOrderNo(), msg);
                    allReleased = false;
                }
            } catch (Exception e) {
                log.error("释放库存异常，SKU：{}，订单号：{}，等待补偿任务处理",
                        item.getSkuId(), orderInfo.getOrderNo(), e);
                allReleased = false;
            }
        }

        if (allReleased) {
            orderInfo.setCancelReason(orderInfo.getCancelReason() + " [已补偿]");
            orderInfoMapper.updateById(orderInfo);
        }

        log.info("订单取消完成，订单号：{}，原因：{}", orderInfo.getOrderNo(), reason);
    }

    private OrderVO buildOrderVO(OrderInfo orderInfo, List<OrderItem> items) {
        OrderVO vo = new OrderVO();
        BeanUtil.copyProperties(orderInfo, vo);

        // MEDIUM-1: 使用 StrUtil.nullToEmpty 处理可能为 null 的地址字段
        // 避免拼接结果出现 "nullnullnull待补充地址" 的情况
        String fullAddress = StrUtil.join("",
                StrUtil.nullToEmpty(orderInfo.getReceiverProvince()),
                StrUtil.nullToEmpty(orderInfo.getReceiverCity()),
                StrUtil.nullToEmpty(orderInfo.getReceiverDistrict()),
                StrUtil.nullToEmpty(orderInfo.getReceiverDetail()));
        vo.setFullAddress(fullAddress);

        // 新I2: null 检查
        Integer statusCode = orderInfo.getStatus();
        OrderStatus status = statusCode != null ? OrderStatus.fromCode(statusCode) : null;
        vo.setStatusDesc(status != null ? status.getDesc() : "未知");

        List<OrderVO.OrderItemVO> itemVOs = items.stream()
                .map(item -> {
                    OrderVO.OrderItemVO itemVO = new OrderVO.OrderItemVO();
                    BeanUtil.copyProperties(item, itemVO);
                    return itemVO;
                })
                .collect(Collectors.toList());
        vo.setItems(itemVOs);

        return vo;
    }
}
