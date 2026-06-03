package com.beibu.mall.product.service;

import com.beibu.mall.product.api.dto.SkuSaveDTO;
import com.beibu.mall.product.api.dto.SkuVO;

import java.util.List;

public interface SkuService {

    List<SkuVO> listSkuBySpuId(Long spuId);

    Long addSku(SkuSaveDTO dto);

    void updateSku(SkuSaveDTO dto);

    void deleteSku(Long skuId);
}
