package com.beibu.mall.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.inventory.entity.InventoryItem;
import com.beibu.mall.inventory.entity.InventoryLog;
import com.beibu.mall.inventory.mapper.InventoryItemMapper;
import com.beibu.mall.inventory.mapper.InventoryLogMapper;
import com.beibu.mall.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存服务实现类
 *
 * @Service 标记这是一个 Service 类，Spring 会自动创建它的实例（Bean）
 * @RequiredArgsConstructor 是 Lombok 注解，自动生成包含 final 字段的构造方法
 * Spring 会通过构造方法自动注入 InventoryItemMapper 等依赖（推荐方式）
 *
 * @Slf4j 是 Lombok 注解，自动生成 log 对象，用于打印日志
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemMapper inventoryItemMapper;
    private final InventoryLogMapper inventoryLogMapper;

    private boolean isOrderIdProcessed(String orderId, String changeType) {
        if (orderId == null) {
            return false;
        }
        InventoryLog existing = inventoryLogMapper.selectOne(
                new LambdaQueryWrapper<InventoryLog>()
                        .eq(InventoryLog::getOrderId, orderId)
                        .eq(InventoryLog::getChangeType, changeType)
        );
        return existing != null;
    }

    /**
     * 扣减库存（直接扣减，不经过预占）
     *
     * 核心思路：直接执行一条原子 UPDATE，不需要先读 version。
     * MySQL InnoDB 的行锁保证同一时刻只有一个线程能更新同一行。
     * available_stock >= quantity 的条件保证不会扣成负数。
     *
     * @Transactional 保证扣减库存和记录流水要么都成功，要么都失败。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductStock(String skuId, int quantity, String orderId) {
        // 0. 参数校验
        if (quantity <= 0) {
            throw new BizException(10034, "扣减数量必须大于0");
        }

        // 0.1 幂等检查（防止重试导致重复扣减）
        if (isOrderIdProcessed(orderId, "DEDUCT")) {
            log.warn("重复扣减，跳过：orderId={}", orderId);
            return;
        }

        // 1. 原子扣减库存（MySQL 行锁保证线程安全）
        int rows = inventoryItemMapper.deductStock(skuId, quantity);

        // 2. 检查是否扣减成功（0行 = 库存不足）
        if (rows == 0) {
            throw new BizException(10031, "库存不足");
        }

        // 3. 扣减成功后，查询当前库存用于记录流水
        InventoryItem item = inventoryItemMapper.selectOne(
                new LambdaQueryWrapper<InventoryItem>().eq(InventoryItem::getSkuId, skuId)
        );

        // 4. 记录库存变动流水（统一记录 totalStock 变化）
        saveInventoryLog(skuId, orderId, "DEDUCT", quantity,
                item.getTotalStock() + quantity, item.getTotalStock(), "直接扣减库存");

        log.info("扣减库存成功：skuId={}, quantity={}, orderId={}", skuId, quantity, orderId);
    }

    /**
     * 预占库存（冻结库存）
     *
     * 预占 = 先把库存"冻结"，等用户支付后再真正扣减。
     * 好处：用户下单到支付这段时间内，库存不会被别人抢走。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void occupyStock(String skuId, int quantity, String orderId) {
        if (quantity <= 0) {
            throw new BizException(10034, "预占数量必须大于0");
        }

        if (isOrderIdProcessed(orderId, "OCCUPY")) {
            log.warn("重复预占，跳过：orderId={}", orderId);
            return;
        }

        int rows = inventoryItemMapper.occupyStock(skuId, quantity);

        if (rows == 0) {
            throw new BizException(10031, "库存不足");
        }

        InventoryItem item = inventoryItemMapper.selectOne(
                new LambdaQueryWrapper<InventoryItem>().eq(InventoryItem::getSkuId, skuId)
        );

        saveInventoryLog(skuId, orderId, "OCCUPY", quantity,
                item.getTotalStock(), item.getTotalStock(), "预占库存");

        log.info("预占库存成功：skuId={}, quantity={}, orderId={}", skuId, quantity, orderId);
    }

    /**
     * 释放预占库存（解冻库存）
     *
     * 用户取消订单或支付超时时，把冻结的库存还回去。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseStock(String skuId, int quantity, String orderId) {
        if (quantity <= 0) {
            throw new BizException(10034, "释放数量必须大于0");
        }

        if (isOrderIdProcessed(orderId, "RELEASE")) {
            log.warn("重复释放，跳过：orderId={}", orderId);
            return;
        }

        int rows = inventoryItemMapper.releaseStock(skuId, quantity);

        if (rows == 0) {
            throw new BizException(10032, "释放库存失败，预占库存不足");
        }

        InventoryItem item = inventoryItemMapper.selectOne(
                new LambdaQueryWrapper<InventoryItem>().eq(InventoryItem::getSkuId, skuId)
        );

        saveInventoryLog(skuId, orderId, "RELEASE", -quantity,
                item.getTotalStock(), item.getTotalStock(), "释放预占库存");

        log.info("释放库存成功：skuId={}, quantity={}, orderId={}", skuId, quantity, orderId);
    }

    /**
     * 确认扣减（从预占转为真正扣减）
     *
     * 用户支付成功后调用，把预占的库存真正扣掉。
     * 注意：available_stock 不变（预占时已经减过了），只减 locked_stock 和 total_stock。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmDeduct(String skuId, int quantity, String orderId) {
        if (quantity <= 0) {
            throw new BizException(10034, "确认扣减数量必须大于0");
        }

        if (isOrderIdProcessed(orderId, "CONFIRM")) {
            log.warn("重复确认扣减，跳过：orderId={}", orderId);
            return;
        }

        int rows = inventoryItemMapper.confirmDeduct(skuId, quantity);

        if (rows == 0) {
            throw new BizException(10033, "确认扣减失败，预占库存不足");
        }

        InventoryItem item = inventoryItemMapper.selectOne(
                new LambdaQueryWrapper<InventoryItem>().eq(InventoryItem::getSkuId, skuId)
        );

        saveInventoryLog(skuId, orderId, "CONFIRM", quantity,
                item.getTotalStock() + quantity, item.getTotalStock(), "确认扣减库存");

        log.info("确认扣减成功：skuId={}, quantity={}, orderId={}", skuId, quantity, orderId);
    }

    private void saveInventoryLog(String skuId, String orderId, String changeType,
                                  int changeQuantity, int beforeStock, int afterStock, String remark) {
        InventoryLog inventoryLog = new InventoryLog();
        inventoryLog.setSkuId(skuId);
        inventoryLog.setOrderId(orderId);
        inventoryLog.setChangeType(changeType);
        inventoryLog.setChangeQuantity(changeQuantity);
        inventoryLog.setBeforeStock(beforeStock);
        inventoryLog.setAfterStock(afterStock);
        inventoryLog.setRemark(remark);
        inventoryLogMapper.insert(inventoryLog);
    }
}