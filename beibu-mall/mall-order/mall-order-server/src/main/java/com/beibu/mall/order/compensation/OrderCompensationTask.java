package com.beibu.mall.order.compensation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beibu.mall.common.result.Result;
import com.beibu.mall.inventory.api.dto.StockOperationDTO;
import com.beibu.mall.inventory.api.feign.InventoryFeignClient;
import com.beibu.mall.order.entity.OrderInfo;
import com.beibu.mall.order.entity.OrderItem;
import com.beibu.mall.order.entity.OrderStatus;
import com.beibu.mall.order.mapper.OrderInfoMapper;
import com.beibu.mall.order.mapper.OrderItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单取消补偿任务
 *
 * 当 cancelOrder() 中库存释放失败时（网络异常、库存服务宕机），
 * 本定时任务会定期扫描已取消的订单，重试释放库存。
 *
 * 执行频率：每 30 秒执行一次
 * 扫描范围：status=CANCELLED 且 cancelTime 在最近 24 小时内的订单
 * 每次最多处理 100 条，避免长时间占用线程
 *
 * 处理策略：
 * - 释放成功：在 cancelReason 追加 [已补偿] 标记，下次扫描跳过
 * - 释放失败：记录日志，下次扫描重试
 * - 24 小时后：不再扫描（避免无限重试）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompensationTask {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;
    private final InventoryFeignClient inventoryFeignClient;

    @Scheduled(fixedDelay = 30000)
    public void compensateCancelledOrderStock() {
        List<OrderInfo> cancelledOrders = orderInfoMapper.selectList(
                new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getStatus, OrderStatus.CANCELLED.getCode())
                        .ge(OrderInfo::getCancelTime, LocalDateTime.now().minusHours(24))
                        .notLike(OrderInfo::getCancelReason, "[已补偿]")
                        .last("LIMIT 100")
        );

        if (cancelledOrders.isEmpty()) {
            return;
        }

        log.info("补偿任务：扫描到 {} 个待补偿订单", cancelledOrders.size());

        for (OrderInfo order : cancelledOrders) {
            try {
                compensateOrderStock(order);
            } catch (Exception e) {
                log.error("补偿任务：处理订单 {} 失败，等待下次重试", order.getOrderNo(), e);
            }
        }
    }

    private void compensateOrderStock(OrderInfo order) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId()));

        boolean allSuccess = true;
        for (OrderItem item : items) {
            try {
                StockOperationDTO stockDTO = new StockOperationDTO();
                stockDTO.setSkuId(String.valueOf(item.getSkuId()));
                stockDTO.setQuantity(item.getQuantity());
                stockDTO.setOrderId(order.getOrderNo());

                Result<Void> result = inventoryFeignClient.releaseStock(stockDTO);
                if (result == null || result.getCode() != 200) {
                    String msg = result != null ? result.getMsg() : "库存服务异常";
                    log.warn("补偿任务：释放库存失败，SKU={}，订单号={}，原因={}",
                            item.getSkuId(), order.getOrderNo(), msg);
                    allSuccess = false;
                } else {
                    log.info("补偿任务：释放库存成功，SKU={}，订单号={}",
                            item.getSkuId(), order.getOrderNo());
                }
            } catch (Exception e) {
                log.error("补偿任务：释放库存异常，SKU={}，订单号={}",
                        item.getSkuId(), order.getOrderNo(), e);
                allSuccess = false;
            }
        }

        if (allSuccess) {
            order.setCancelReason(order.getCancelReason() + " [已补偿]");
            orderInfoMapper.updateById(order);
            log.info("补偿任务：订单 {} 库存释放完成", order.getOrderNo());
        }
    }
}
