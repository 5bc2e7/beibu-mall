package com.beibu.mall.product.service;

import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.product.api.dto.SkuSaveDTO;
import com.beibu.mall.product.api.dto.SkuVO;
import com.beibu.mall.product.entity.Sku;
import com.beibu.mall.product.entity.Spu;
import com.beibu.mall.product.mapper.SkuMapper;
import com.beibu.mall.product.mapper.SpuMapper;
import com.beibu.mall.product.service.impl.SkuServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkuServiceTest {

    @Mock
    private SkuMapper skuMapper;

    @Mock
    private SpuMapper spuMapper;

    @InjectMocks
    private SkuServiceImpl skuService;

    private Sku testSku;
    private Spu testSpu;
    private SkuSaveDTO saveDTO;

    @BeforeEach
    void setUp() {
        testSpu = new Spu();
        testSpu.setId(1L);
        testSpu.setName("北部湾大对虾");

        testSku = new Sku();
        testSku.setId(1L);
        testSku.setSpuId(1L);
        testSku.setSpec("鲜活/500g");
        testSku.setPrice(new BigDecimal("89.00"));
        testSku.setUnit("g");
        testSku.setStock(100);
        testSku.setStatus(1);

        saveDTO = new SkuSaveDTO();
        saveDTO.setSpuId(1L);
        saveDTO.setSpec("鲜活/500g");
        saveDTO.setPrice(new BigDecimal("89.00"));
        saveDTO.setUnit("g");
        saveDTO.setStock(100);
    }

    @Test
    @DisplayName("获取 SKU 列表 - 成功")
    void listSkuBySpuId_success() {
        when(skuMapper.selectList(any())).thenReturn(Arrays.asList(testSku));

        List<SkuVO> result = skuService.listSkuBySpuId(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("鲜活/500g", result.get(0).getSpec());
    }

    @Test
    @DisplayName("添加 SKU - 成功")
    void addSku_success() {
        when(spuMapper.selectById(1L)).thenReturn(testSpu);
        doAnswer(invocation -> {
            Sku sku = invocation.getArgument(0);
            sku.setId(1L);
            return 1;
        }).when(skuMapper).insert(any(Sku.class));

        Long skuId = skuService.addSku(saveDTO);

        assertNotNull(skuId);
        assertEquals(1L, skuId);
        verify(skuMapper, times(1)).insert(any(Sku.class));
    }

    @Test
    @DisplayName("添加 SKU - SPU 不存在")
    void addSpu_spuNotFound() {
        when(spuMapper.selectById(999L)).thenReturn(null);
        saveDTO.setSpuId(999L);

        BizException exception = assertThrows(BizException.class,
                () -> skuService.addSku(saveDTO));

        assertEquals(40013, exception.getCode());
        assertEquals("SPU 不存在", exception.getMessage());
    }

    @Test
    @DisplayName("修改 SKU - 成功")
    void updateSku_success() {
        saveDTO.setId(1L);
        when(skuMapper.selectById(1L)).thenReturn(testSku);
        when(skuMapper.updateById(any(Sku.class))).thenReturn(1);

        assertDoesNotThrow(() -> skuService.updateSku(saveDTO));

        verify(skuMapper, times(1)).updateById(any(Sku.class));
    }

    @Test
    @DisplayName("修改 SKU - SKU 不存在")
    void updateSku_notFound() {
        saveDTO.setId(999L);
        when(skuMapper.selectById(999L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> skuService.updateSku(saveDTO));

        assertEquals(40012, exception.getCode());
    }

    @Test
    @DisplayName("删除 SKU - 成功（软删除）")
    void deleteSku_success() {
        when(skuMapper.selectById(1L)).thenReturn(testSku);
        when(skuMapper.updateById(any(Sku.class))).thenReturn(1);

        assertDoesNotThrow(() -> skuService.deleteSku(1L));

        assertEquals(0, testSku.getStatus());
        verify(skuMapper, times(1)).updateById(any(Sku.class));
    }

    @Test
    @DisplayName("删除 SKU - SKU 不存在")
    void deleteSku_notFound() {
        when(skuMapper.selectById(999L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> skuService.deleteSku(999L));

        assertEquals(40012, exception.getCode());
    }
}
