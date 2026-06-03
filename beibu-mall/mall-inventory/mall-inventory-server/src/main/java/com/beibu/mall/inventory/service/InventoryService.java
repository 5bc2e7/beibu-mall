package com.beibu.mall.inventory.service;

/**
 * 库存服务接口
 *
 * 接口的作用：定义"能做什么"，不关心"怎么做"。
 * 好处：
 * 1. 解耦：Controller 只依赖接口，不关心实现类是谁
 * 2. 可测试：单元测试时可以用 Mock 实现类
 * 3. 可替换：以后换实现（比如加缓存）不用改 Controller
 *
 * 库存服务的核心职责：
 * - 扣减库存（防超卖）
 * - 预占库存（冻结）
 * - 释放库存（解冻）
 * - 确认扣减（从预占转为真正扣减）
 */
public interface InventoryService {

    /**
     * 扣减库存（直接扣减，不经过预占）
     *
     * 使用场景：库存足够时直接扣减，不需要预占流程
     * 实现原理：乐观锁 + 原子SQL，防止并发超卖
     *
     * @param skuId 商品SKU ID
     * @param quantity 要扣减的数量
     * @param orderId 关联的订单ID（用于记录流水）
     * @throws com.beibu.mall.common.exception.BizException 错误码10031，库存不足
     */
    void deductStock(String skuId, int quantity, String orderId);

    /**
     * 预占库存（冻结库存）
     *
     * 使用场景：用户下单但还没支付时，先把库存冻结起来
     * 防止这段时间别人把库存买走
     *
     * @param skuId 商品SKU ID
     * @param quantity 要预占的数量
     * @param orderId 关联的订单ID
     * @throws com.beibu.mall.common.exception.BizException 错误码10031，库存不足
     */
    void occupyStock(String skuId, int quantity, String orderId);

    /**
     * 释放预占库存（解冻库存）
     *
     * 使用场景：用户取消订单或支付超时时，把冻结的库存还回去
     *
     * @param skuId 商品SKU ID
     * @param quantity 要释放的数量
     * @param orderId 关联的订单ID
     */
    void releaseStock(String skuId, int quantity, String orderId);

    /**
     * 确认扣减（从预占转为真正扣减）
     *
     * 使用场景：用户支付成功后，调用这个方法
     * 把预占的库存真正扣掉
     *
     * @param skuId 商品SKU ID
     * @param quantity 要确认扣减的数量
     * @param orderId 关联的订单ID
     */
    void confirmDeduct(String skuId, int quantity, String orderId);
}