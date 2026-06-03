package com.beibu.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.product.api.dto.SkuSaveDTO;
import com.beibu.mall.product.api.dto.SkuVO;
import com.beibu.mall.product.entity.Sku;
import com.beibu.mall.product.entity.Spu;
import com.beibu.mall.product.mapper.SkuMapper;
import com.beibu.mall.product.mapper.SpuMapper;
import com.beibu.mall.product.service.SkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * SKU 服务实现类
 *
 * SKU = Stock Keeping Unit（库存量单位）
 * 是商品的具体规格，如「鲜活/500g/¥89」。
 */
@Service
@RequiredArgsConstructor
public class SkuServiceImpl implements SkuService {

    private final SkuMapper skuMapper;
    private final SpuMapper spuMapper;

    @Override
    public SkuVO getSkuById(Long skuId) {
        Sku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            return null;
        }

        SkuVO vo = new SkuVO();
        vo.setId(sku.getId());
        vo.setSpuId(sku.getSpuId());
        vo.setSpec(sku.getSpec());
        vo.setPrice(sku.getPrice());
        vo.setUnit(sku.getUnit());
        vo.setStock(sku.getStock());
        vo.setImg(sku.getImg());
        vo.setStatus(sku.getStatus());
        return vo;
    }

    /**
     * 获取 SPU 下的所有 SKU
     *
     * 实现思路：
     * 1. 根据 spuId 查询该 SPU 下所有启用的 SKU
     * 2. 转换为 VO 列表返回
     */
    @Override
    public List<SkuVO> listSkuBySpuId(Long spuId) {
        // 1. 查询该 SPU 下所有启用的 SKU
        List<Sku> skuList = skuMapper.selectList(
                new LambdaQueryWrapper<Sku>()
                        .eq(Sku::getSpuId, spuId)
                        .eq(Sku::getStatus, 1)  // 只查启用的
                        .orderByAsc(Sku::getId)  // 按 ID 排序
        );

        // 2. 转换为 VO 列表
        List<SkuVO> voList = new ArrayList<>();
        for (Sku sku : skuList) {
            SkuVO vo = new SkuVO();
            vo.setId(sku.getId());
            vo.setSpuId(sku.getSpuId());
            vo.setSpec(sku.getSpec());
            vo.setPrice(sku.getPrice());
            vo.setUnit(sku.getUnit());
            vo.setStock(sku.getStock());
            vo.setImg(sku.getImg());
            vo.setStatus(sku.getStatus());
            voList.add(vo);
        }

        return voList;
    }

    @Override
    @Transactional
    public Long addSku(SkuSaveDTO dto) {
        Spu spu = spuMapper.selectById(dto.getSpuId());
        if (spu == null) {
            throw new BizException(40013, "SPU 不存在");
        }

        Sku sku = new Sku();
        sku.setSpuId(dto.getSpuId());
        sku.setSpec(dto.getSpec());
        sku.setPrice(dto.getPrice());
        sku.setUnit(dto.getUnit() != null ? dto.getUnit() : "件");
        sku.setStock(dto.getStock() != null ? dto.getStock() : 0);
        sku.setImg(dto.getImg());
        sku.setStatus(1);

        skuMapper.insert(sku);
        return sku.getId();
    }

    @Override
    @Transactional
    public void updateSku(SkuSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(40011, "SKU ID 不能为空");
        }

        Sku sku = skuMapper.selectById(dto.getId());
        if (sku == null) {
            throw new BizException(40012, "SKU 不存在");
        }

        if (dto.getSpec() != null) {
            sku.setSpec(dto.getSpec());
        }
        if (dto.getPrice() != null) {
            sku.setPrice(dto.getPrice());
        }
        if (dto.getUnit() != null) {
            sku.setUnit(dto.getUnit());
        }
        if (dto.getStock() != null) {
            sku.setStock(dto.getStock());
        }
        if (dto.getImg() != null) {
            sku.setImg(dto.getImg());
        }

        skuMapper.updateById(sku);
    }

    @Override
    @Transactional
    public void deleteSku(Long skuId) {
        Sku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(40012, "SKU 不存在");
        }
        sku.setStatus(0);
        skuMapper.updateById(sku);
    }
}
