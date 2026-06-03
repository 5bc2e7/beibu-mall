package com.beibu.mall.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beibu.mall.product.entity.Sku;
import org.apache.ibatis.annotations.Mapper;

/**
 * SKU Mapper 接口
 *
 * SKU = Stock Keeping Unit（库存量单位）
 * 是商品的具体规格，如「鲜活/500g/¥89」。
 */
@Mapper
public interface SkuMapper extends BaseMapper<Sku> {
}
