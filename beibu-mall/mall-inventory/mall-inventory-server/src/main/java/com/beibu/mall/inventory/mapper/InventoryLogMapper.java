package com.beibu.mall.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beibu.mall.inventory.entity.InventoryLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存变动流水 Mapper 接口
 *
 * @Mapper 告诉 MyBatis 这是一个 Mapper 接口，会自动生成实现类并注册到 Spring 容器。
 *
 * 继承 BaseMapper<InventoryLog> 后，自动拥有基本的 CRUD 方法。
 * 库存变动流水主要是插入和查询，不需要复杂的自定义方法。
 */
@Mapper
public interface InventoryLogMapper extends BaseMapper<InventoryLog> {
    // BaseMapper 已经提供了基本的 CRUD，不需要额外定义方法
}