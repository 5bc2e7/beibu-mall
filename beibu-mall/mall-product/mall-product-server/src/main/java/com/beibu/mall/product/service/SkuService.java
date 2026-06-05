package com.beibu.mall.product.service;

import com.beibu.mall.product.api.dto.SkuSaveDTO;
import com.beibu.mall.product.api.dto.SkuVO;
import com.beibu.mall.product.entity.Sku;

import java.util.List;

public interface SkuService {

    SkuVO getSkuById(Long skuId);

    List<SkuVO> listSkuBySpuId(Long spuId);

    List<Sku> listSkuEntityBySpuId(Long spuId);

    Long addSku(SkuSaveDTO dto);

    void updateSku(SkuSaveDTO dto);

    void deleteSku(Long skuId);
}
