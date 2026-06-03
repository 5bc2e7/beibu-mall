package com.beibu.mall.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beibu.mall.product.entity.Spu;
import org.apache.ibatis.annotations.Mapper;

/**
 * SPU Mapper 接口
 *
 * SPU = Standard Product Unit（标准产品单元）
 * 例如「北部湾大对虾」就是一个 SPU。
 */
@Mapper
public interface SpuMapper extends BaseMapper<Spu> {
}
