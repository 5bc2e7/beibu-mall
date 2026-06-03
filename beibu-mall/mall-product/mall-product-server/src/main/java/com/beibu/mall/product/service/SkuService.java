package com.beibu.mall.product.service;

import com.beibu.mall.product.api.dto.SkuSaveDTO;
import com.beibu.mall.product.api.dto.SkuVO;

import java.util.List;

public interface SkuService {

    /**
     * 根据 SKU ID 查询单个 SKU
     *
     * @param skuId SKU ID
     * @return SKU 信息，不存在则返回 null
     */
    SkuVO getSkuById(Long skuId);

    List<SkuVO> listSkuBySpuId(Long spuId);

    Long addSku(SkuSaveDTO dto);

    void updateSku(SkuSaveDTO dto);

    void deleteSku(Long skuId);
}
