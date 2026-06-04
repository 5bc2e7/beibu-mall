package com.beibu.mall.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beibu.mall.order.api.dto.CreateOrderDTO;
import com.beibu.mall.order.api.dto.OrderVO;

/**
 * 订单服务接口
 *
 * Service = 业务逻辑层
 * 作用：处理业务逻辑，协调 Mapper（数据库操作）和 Controller（接口暴露）
 *
 * 为什么需要接口？
 * 1. 解耦：Controller 只依赖接口，不关心具体实现
 * 2. 可测试：单元测试时可以用 Mock 实现
 * 3. 可替换：以后换实现（比如加缓存）不用改 Controller
 */
public interface OrderService {

    /**
     * 创建订单
     *
     * 业务流程：
     * 1. 校验参数
     * 2. 调用商品服务查询价格
     * 3. 调用库存服务预占库存
     * 4. 生成订单号（雪花算法）
     * 5. 保存订单和订单明细
     * 6. 返回订单信息
     *
     * @param createOrderDTO 下单请求参数
     * @param userId 当前登录用户ID
     * @return 订单详情
     */
    OrderVO createOrder(CreateOrderDTO createOrderDTO, Long userId);

    /**
     * 查询我的订单（分页）
     *
     * @param userId 用户ID
     * @param page 页码（从1开始）
     * @param size 每页数量
     * @return 分页结果
     */
    Page<OrderVO> listMyOrders(Long userId, int page, int size);

    /**
     * 查询订单详情
     *
     * @param orderId 订单ID
     * @param userId 用户ID（校验权限，只能看自己的订单）
     * @return 订单详情
     */
    OrderVO getOrderDetail(Long orderId, Long userId);

    /**
     * 取消订单
     *
     * 业务流程：
     * 1. 校验订单存在且属于当前用户
     * 2. 校验订单状态（只有待支付的订单才能取消）
     * 3. 调用库存服务释放预占库存
     * 4. 更新订单状态为已取消
     *
     * @param orderId 订单ID
     * @param userId 用户ID
     * @param reason 取消原因（可选）
     */
    void cancelOrder(Long orderId, Long userId, String reason);

    /**
     * 处理支付成功
     *
     * 业务流程：
     * 1. 校验订单存在且状态为待支付
     * 2. 更新订单状态为已支付，记录支付时间
     * 3. 调用库存服务确认扣减（预占转实扣）
     *
     * @param orderNo 业务订单号
     * @param paymentId 支付单ID
     * @param amount 支付金额
     * @param paymentTime 支付时间
     */
    void handlePaymentSuccess(String orderNo, Long paymentId,
                              java.math.BigDecimal amount, java.time.LocalDateTime paymentTime);

    /**
     * 取消超时未支付订单
     *
     * 业务流程：
     * 1. 查询订单状态
     * 2. 如果订单仍然是"待支付"状态，则取消订单
     * 3. 释放预占库存
     *
     * @param orderNo 业务订单号
     */
    void cancelTimeoutOrder(String orderNo);
}
