package com.beibu.mall.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beibu.mall.product.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类 Mapper 接口
 *
 * 继承 BaseMapper<Category> 后，自动拥有 CRUD 方法：
 * - insert, deleteById, updateById, selectById, selectList 等
 * 不需要手写 SQL，MyBatis-Plus 自动生成。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
