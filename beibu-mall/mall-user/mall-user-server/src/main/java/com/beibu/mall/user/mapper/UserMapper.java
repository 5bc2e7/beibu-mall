package com.beibu.mall.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beibu.mall.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper 接口
 *
 * @Mapper 告诉 MyBatis 这是一个 Mapper 接口，会自动生成实现类并注册到 Spring 容器。
 *
 * 继承 BaseMapper<User> 后，自动拥有以下方法（不用写 SQL）：
 * - insert(User)           插入一条记录
 * - deleteById(Long id)    按 ID 删除（逻辑删除）
 * - updateById(User)       按 ID 更新（只更新非 null 字段）
 * - selectById(Long id)    按 ID 查询
 * - selectList(Wrapper)    条件查询
 * - selectOne(Wrapper)     查询单条
 * - selectCount(Wrapper)   统计数量
 *
 * 如果需要复杂的 SQL（多表联查、自定义条件），再手写 XML 或用 @Select 注解。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // BaseMapper 已经提供了基本的 CRUD，不需要额外定义方法
}
