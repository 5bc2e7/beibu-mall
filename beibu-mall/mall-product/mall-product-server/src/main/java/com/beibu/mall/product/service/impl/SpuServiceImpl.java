package com.beibu.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.product.api.dto.PageResult;
import com.beibu.mall.product.api.dto.SkuVO;
import com.beibu.mall.product.api.dto.SpuDetailVO;
import com.beibu.mall.product.api.dto.SpuQueryDTO;
import com.beibu.mall.product.api.dto.SpuSaveDTO;
import com.beibu.mall.product.api.dto.SpuVO;
import com.beibu.mall.product.entity.Category;
import com.beibu.mall.product.entity.Spu;
import com.beibu.mall.product.mapper.CategoryMapper;
import com.beibu.mall.product.mapper.SpuMapper;
import com.beibu.mall.product.service.SkuService;
import com.beibu.mall.product.service.SpuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SPU 服务实现类
 *
 * SPU = Standard Product Unit（标准产品单元）
 * 例如「北部湾大对虾」就是一个 SPU。
 *
 * 这个类负责商品的增删改查和上下架操作。
 */
@Service
@RequiredArgsConstructor
public class SpuServiceImpl implements SpuService {

    private final SpuMapper spuMapper;
    private final SkuService skuService;
    private final CategoryMapper categoryMapper;

    /**
     * 商品列表分页查询
     *
     * 实现思路：
     * 1. 构建查询条件（分类ID、关键词、状态）
     * 2. 使用 MyBatis-Plus 的 Page 进行分页
     * 3. 返回分页结果
     */
    @Override
    public PageResult<SpuVO> listSpu(SpuQueryDTO query) {
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<>();

        if (query.getCategoryId() != null) {
            wrapper.eq(Spu::getCategoryId, query.getCategoryId());
        }

        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(Spu::getName, query.getKeyword());
        }

        if (query.getStatus() != null) {
            wrapper.eq(Spu::getStatus, query.getStatus());
        }

        wrapper.orderByDesc(Spu::getCreateTime);

        Page<Spu> page = new Page<>(query.getPage(), query.getSize());
        Page<Spu> result = spuMapper.selectPage(page, wrapper);

        // 批量查询分类名称，避免 N+1 问题
        Set<Long> categoryIds = result.getRecords().stream()
                .map(Spu::getCategoryId)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNameMap = getCategoryNameMap(categoryIds);

        List<SpuVO> voList = new ArrayList<>();
        for (Spu spu : result.getRecords()) {
            SpuVO vo = new SpuVO();
            vo.setId(spu.getId());
            vo.setCategoryId(spu.getCategoryId());
            vo.setCategoryName(categoryNameMap.get(spu.getCategoryId()));
            vo.setName(spu.getName());
            vo.setOrigin(spu.getOrigin());
            vo.setIsFresh(spu.getIsFresh());
            vo.setPriceType(spu.getPriceType());
            vo.setMinBuy(spu.getMinBuy());
            vo.setStatus(spu.getStatus());
            voList.add(vo);
        }

        return new PageResult<>(voList, result.getTotal(), query.getPage(), query.getSize());
    }

    /**
     * 获取商品详情（含所有 SKU）
     *
     * 实现思路：
     * 1. 根据 spuId 查询 SPU 信息
     * 2. 查询该 SPU 下的所有 SKU
     * 3. 组装成 SpuDetailVO 返回
     */
    @Override
    public SpuDetailVO getSpuDetail(Long spuId) {
        Spu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new BizException(40004, "商品不存在");
        }

        SpuDetailVO vo = new SpuDetailVO();
        vo.setId(spu.getId());
        vo.setCategoryId(spu.getCategoryId());
        vo.setCategoryName(getCategoryName(spu.getCategoryId()));
        vo.setName(spu.getName());
        vo.setOrigin(spu.getOrigin());
        vo.setIsFresh(spu.getIsFresh());
        vo.setPriceType(spu.getPriceType());
        vo.setShelfLife(spu.getShelfLife());
        vo.setMinBuy(spu.getMinBuy());
        vo.setDescription(spu.getDescription());
        vo.setStatus(spu.getStatus());

        List<SkuVO> skuList = skuService.listSkuBySpuId(spuId);
        vo.setSkuList(skuList);

        return vo;
    }

    /**
     * 添加商品
     *
     * 实现思路：
     * 1. 将 DTO 转换为实体
     * 2. 设置默认值
     * 3. 插入数据库
     */
    @Override
    @Transactional
    public Long addSpu(SpuSaveDTO dto) {
        validateCategory(dto.getCategoryId());

        Spu spu = new Spu();
        spu.setCategoryId(dto.getCategoryId());
        spu.setName(dto.getName());
        spu.setOrigin(dto.getOrigin());
        spu.setIsFresh(dto.getIsFresh() != null ? dto.getIsFresh() : 0);
        spu.setPriceType(dto.getPriceType() != null ? dto.getPriceType() : 0);
        spu.setShelfLife(dto.getShelfLife());
        spu.setMinBuy(dto.getMinBuy() != null ? dto.getMinBuy() : 1);
        spu.setDescription(dto.getDescription());
        spu.setStatus(0);

        spuMapper.insert(spu);
        return spu.getId();
    }

    /**
     * 修改商品
     *
     * 实现思路：
     * 1. 检查商品是否存在
     * 2. 更新商品信息
     */
    @Override
    @Transactional
    public void updateSpu(SpuSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(40004, "商品ID不能为空");
        }

        Spu spu = spuMapper.selectById(dto.getId());
        if (spu == null) {
            throw new BizException(40004, "商品不存在");
        }

        if (dto.getCategoryId() != null) {
            validateCategory(dto.getCategoryId());
            spu.setCategoryId(dto.getCategoryId());
        }
        if (dto.getName() != null) {
            spu.setName(dto.getName());
        }
        if (dto.getOrigin() != null) {
            spu.setOrigin(dto.getOrigin());
        }
        if (dto.getIsFresh() != null) {
            spu.setIsFresh(dto.getIsFresh());
        }
        if (dto.getPriceType() != null) {
            spu.setPriceType(dto.getPriceType());
        }
        if (dto.getShelfLife() != null) {
            spu.setShelfLife(dto.getShelfLife());
        }
        if (dto.getMinBuy() != null) {
            spu.setMinBuy(dto.getMinBuy());
        }
        if (dto.getDescription() != null) {
            spu.setDescription(dto.getDescription());
        }

        spuMapper.updateById(spu);
    }

    /**
     * 上架商品
     *
     * 只有下架状态的商品才能上架。
     */
    @Override
    @Transactional
    public void onSale(Long spuId) {
        Spu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new BizException(40004, "商品不存在");
        }

        if (spu.getStatus() == 1) {
            throw new BizException(40008, "商品已上架，不能重复上架");
        }

        spu.setStatus(1);
        spuMapper.updateById(spu);
    }

    /**
     * 下架商品
     *
     * 只有上架状态的商品才能下架。
     */
    @Override
    @Transactional
    public void offSale(Long spuId) {
        Spu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new BizException(40004, "商品不存在");
        }

        if (spu.getStatus() == 0) {
            throw new BizException(40007, "商品已下架，不能重复下架");
        }

        spu.setStatus(0);
        spuMapper.updateById(spu);
    }

    private Map<Long, String> getCategoryNameMap(Set<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        List<Category> categories = categoryMapper.selectBatchIds(categoryIds);
        return categories.stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private String getCategoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryMapper.selectById(categoryId);
        return category != null ? category.getName() : null;
    }

    private void validateCategory(Long categoryId) {
        if (categoryId == null) {
            throw new BizException(40009, "分类ID不能为空");
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(40010, "分类不存在");
        }
    }
}
