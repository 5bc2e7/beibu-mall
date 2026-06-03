package com.beibu.mall.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beibu.mall.inventory.entity.InventoryItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 库存 Mapper 接口
 *
 * @Mapper 告诉 MyBatis 这是一个 Mapper 接口，会自动生成实现类并注册到 Spring 容器。
 *
 * 继承 BaseMapper<InventoryItem> 后，自动拥有以下方法（不用写 SQL）：
 * - insert(InventoryItem)           插入一条记录
 * - deleteById(Long id)             按 ID 删除
 * - updateById(InventoryItem)       按 ID 更新（只更新非 null 字段）
 * - selectById(Long id)             按 ID 查询
 * - selectList(Wrapper)             条件查询
 * - selectOne(Wrapper)              查询单条
 * - selectCount(Wrapper)            统计数量
 *
 * 这里还定义了自定义的扣减库存方法，使用乐观锁防止超卖。
 */
@Mapper
public interface InventoryItemMapper extends BaseMapper<InventoryItem> {

    /**
     * 扣减库存（原子操作，防超卖）
     *
     * 为什么不需要 version 条件？
     * MySQL 的 InnoDB 引擎在执行 UPDATE 时会自动加行锁（悲观锁），
     * 同一时刻只有一个线程能更新这一行。
     * 所以 available_stock = available_stock - 1 这个操作天然是线程安全的。
     *
     * 那 version 有什么用？
     * version 是给业务层做乐观锁用的——当需要先读后改（select+update）时，
     * 可以通过 version 检测"读取后是否被别人改过"。
     * 但扣减库存这种场景，直接一条 UPDATE 就够了，不需要先读。
     *
     * 如果这条 SQL 影响了 0 行，说明库存不足，抛 BizException(10031,"库存不足")
     *
     * @param skuId 商品SKU ID
     * @param quantity 要扣减的数量
     * @return 影响的行数（1=成功，0=失败）
     */
    @Update("UPDATE inventory_item " +
            "SET available_stock = available_stock - #{quantity}, " +
            "    version = version + 1 " +
            "WHERE sku_id = #{skuId} " +
            "  AND available_stock >= #{quantity}")
    int deductStock(@Param("skuId") String skuId,
                    @Param("quantity") int quantity);

    /**
     * 预占库存（冻结库存）
     *
     * 预占的意思是：用户下单了但还没支付，先把库存"冻结"起来，
     * 防止这段时间别人把库存买走。
     *
     * 预占后：
     * - available_stock 减少（不能再卖给别人了）
     * - locked_stock 增加（被冻结了）
     *
     * 等用户支付成功后，再调用"确认扣减"，把 locked_stock 也减掉。
     * 如果用户超时没支付，就调用"释放库存"，把冻结的库存还回去。
     *
     * @param skuId 商品SKU ID
     * @param quantity 要预占的数量
     * @return 影响的行数（1=成功，0=失败）
     */
    @Update("UPDATE inventory_item " +
            "SET available_stock = available_stock - #{quantity}, " +
            "    locked_stock = locked_stock + #{quantity}, " +
            "    version = version + 1 " +
            "WHERE sku_id = #{skuId} " +
            "  AND available_stock >= #{quantity}")
    int occupyStock(@Param("skuId") String skuId,
                    @Param("quantity") int quantity);

    /**
     * 释放预占库存（解冻库存）
     *
     * 当用户取消订单或支付超时时，把冻结的库存还回去。
     *
     * 释放后：
     * - available_stock 增加（又可以卖给别人了）
     * - locked_stock 减少（不再冻结了）
     *
     * @param skuId 商品SKU ID
     * @param quantity 要释放的数量
     * @return 影响的行数（1=成功，0=失败）
     */
    @Update("UPDATE inventory_item " +
            "SET available_stock = available_stock + #{quantity}, " +
            "    locked_stock = locked_stock - #{quantity}, " +
            "    version = version + 1 " +
            "WHERE sku_id = #{skuId} " +
            "  AND locked_stock >= #{quantity}")
    int releaseStock(@Param("skuId") String skuId,
                     @Param("quantity") int quantity);

    /**
     * 确认扣减（从预占转为真正扣减）
     *
     * 当用户支付成功后，调用这个方法：
     * - locked_stock 减少（不再冻结了）
     * - total_stock 也减少（真正扣掉了）
     *
     * 注意：available_stock 不变，因为预占时已经减过了。
     *
     * @param skuId 商品SKU ID
     * @param quantity 要确认扣减的数量
     * @return 影响的行数（1=成功，0=失败）
     */
    @Update("UPDATE inventory_item " +
            "SET locked_stock = locked_stock - #{quantity}, " +
            "    total_stock = total_stock - #{quantity}, " +
            "    version = version + 1 " +
            "WHERE sku_id = #{skuId} " +
            "  AND locked_stock >= #{quantity}")
    int confirmDeduct(@Param("skuId") String skuId,
                      @Param("quantity") int quantity);
}