package com.beibu.mall.product.service;

import com.beibu.mall.product.api.dto.PageResult;
import com.beibu.mall.product.api.dto.SpuDetailVO;
import com.beibu.mall.product.api.dto.SpuQueryDTO;
import com.beibu.mall.product.api.dto.SpuSaveDTO;
import com.beibu.mall.product.api.dto.SpuVO;

public interface SpuService {

    PageResult<SpuVO> listSpu(SpuQueryDTO query);

    /**
     * 获取商品详情（含所有 SKU）
     *
     * @param spuId SPU ID
     * @return 商品详情，包含 SPU 信息和 SKU 列表
     */
    SpuDetailVO getSpuDetail(Long spuId);

    /**
     * 添加商品
     *
     * @param dto 商品信息
     * @return 新增的 SPU ID
     */
    Long addSpu(SpuSaveDTO dto);

    /**
     * 修改商品
     *
     * @param dto 商品信息（必须包含 id）
     */
    void updateSpu(SpuSaveDTO dto);

    /**
     * 上架商品
     *
     * @param spuId SPU ID
     */
    void onSale(Long spuId);

    /**
     * 下架商品
     *
     * @param spuId SPU ID
     */
    void offSale(Long spuId);
}
