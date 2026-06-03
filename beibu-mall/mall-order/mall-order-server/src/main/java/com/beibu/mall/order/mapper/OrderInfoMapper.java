package com.beibu.mall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beibu.mall.order.entity.OrderInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单主表 Mapper 接口
 *
 * Mapper = 数据访问层（DAO，Data Access Object）
 * 作用：操作数据库，执行 SQL 语句
 *
 * 为什么继承 BaseMapper？
 * BaseMapper 是 MyBatis-Plus 提供的，已经内置了常用的 CRUD 方法：
 * - insert：插入数据
 * - deleteById：根据ID删除
 * - updateById：根据ID更新
 * - selectById：根据ID查询
 * - selectList：查询列表
 * - selectPage：分页查询
 * 这些方法不用我们写 SQL，MyBatis-Plus 自动生成。
 *
 * @Mapper 注解：告诉 MyBatis 这是一个 Mapper 接口，需要生成代理对象
 */
@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {
    // 继承 BaseMapper 已经提供了基本的 CRUD 方法
    // 如果需要复杂查询，可以在这里自定义方法，用 @Select 注解写 SQL
}
