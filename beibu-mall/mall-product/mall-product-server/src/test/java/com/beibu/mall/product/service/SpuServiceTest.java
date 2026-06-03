package com.beibu.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.product.api.dto.PageResult;
import com.beibu.mall.product.api.dto.SpuDetailVO;
import com.beibu.mall.product.api.dto.SpuQueryDTO;
import com.beibu.mall.product.api.dto.SpuSaveDTO;
import com.beibu.mall.product.api.dto.SpuVO;
import com.beibu.mall.product.entity.Spu;
import com.beibu.mall.product.mapper.SpuMapper;
import com.beibu.mall.product.service.impl.SpuServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;

/**
 * SpuService 单元测试
 *
 * 测试 SPU 服务的业务逻辑，包括：
 * - 商品列表分页查询
 * - 商品详情查询
 * - 添加商品
 * - 修改商品
 * - 上架/下架商品
 */
@ExtendWith(MockitoExtension.class)
class SpuServiceTest {

    @Mock
    private SpuMapper spuMapper;

    @Mock
    private com.beibu.mall.product.mapper.CategoryMapper categoryMapper;

    @Mock
    private com.beibu.mall.product.service.SkuService skuService;

    @InjectMocks
    private SpuServiceImpl spuService;

    private Spu testSpu;
    private SpuSaveDTO saveDTO;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        testSpu = new Spu();
        testSpu.setId(1L);
        testSpu.setCategoryId(6L);
        testSpu.setName("北部湾大对虾");
        testSpu.setOrigin("广西北海");
        testSpu.setIsFresh(1);
        testSpu.setPriceType(1);
        testSpu.setShelfLife(24);
        testSpu.setMinBuy(1);
        testSpu.setDescription("北部湾特产");
        testSpu.setStatus(0);  // 下架状态

        saveDTO = new SpuSaveDTO();
        saveDTO.setCategoryId(6L);
        saveDTO.setName("北部湾大对虾");
        saveDTO.setOrigin("广西北海");
        saveDTO.setIsFresh(1);
        saveDTO.setPriceType(1);
        saveDTO.setShelfLife(24);
        saveDTO.setMinBuy(1);
        saveDTO.setDescription("北部湾特产");
    }

    @Test
    @DisplayName("商品列表分页查询 - 成功")
    void listSpu_success() {
        // given
        SpuQueryDTO query = new SpuQueryDTO();
        query.setPage(1);
        query.setSize(10);

        Page<Spu> page = new Page<>(1, 10);
        page.setRecords(java.util.Arrays.asList(testSpu));
        page.setTotal(1);

        when(spuMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        // when
        PageResult<SpuVO> result = spuService.listSpu(query);

        assertNotNull(result);
        assertNotNull(result.getList());
        assertEquals(1L, result.getTotal());
    }

    @Test
    @DisplayName("商品详情 - 成功")
    void getSpuDetail_success() {
        when(spuMapper.selectById(1L)).thenReturn(testSpu);
        when(skuService.listSkuBySpuId(1L)).thenReturn(java.util.Arrays.asList(new com.beibu.mall.product.api.dto.SkuVO()));

        SpuDetailVO detail = spuService.getSpuDetail(1L);

        assertNotNull(detail);
        assertEquals(1L, detail.getId());
        assertEquals("北部湾大对虾", detail.getName());
        assertEquals("广西北海", detail.getOrigin());
        assertNotNull(detail.getSkuList());
        assertEquals(1, detail.getSkuList().size());
        verify(skuService).listSkuBySpuId(1L);
    }

    @Test
    @DisplayName("商品详情 - 商品不存在")
    void getSpuDetail_notFound() {
        // given
        when(spuMapper.selectById(999L)).thenReturn(null);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> spuService.getSpuDetail(999L));

        assertEquals(40004, exception.getCode());
        assertEquals("商品不存在", exception.getMessage());
    }

    @Test
    @DisplayName("添加商品 - 成功")
    void addSpu_success() {
        doAnswer(invocation -> {
            Spu spu = invocation.getArgument(0);
            spu.setId(1L);
            return 1;
        }).when(spuMapper).insert(any(Spu.class));
        when(categoryMapper.selectById(6L)).thenReturn(new com.beibu.mall.product.entity.Category());

        Long spuId = spuService.addSpu(saveDTO);

        assertNotNull(spuId);
        assertEquals(1L, spuId);
        verify(spuMapper, times(1)).insert(any(Spu.class));
    }

    @Test
    @DisplayName("修改商品 - 成功")
    void updateSpu_success() {
        saveDTO.setId(1L);
        when(spuMapper.selectById(1L)).thenReturn(testSpu);
        when(spuMapper.updateById(any(Spu.class))).thenReturn(1);
        when(categoryMapper.selectById(6L)).thenReturn(new com.beibu.mall.product.entity.Category());

        assertDoesNotThrow(() -> spuService.updateSpu(saveDTO));

        verify(spuMapper, times(1)).updateById(any(Spu.class));
    }

    @Test
    @DisplayName("修改商品 - 商品不存在")
    void updateSpu_notFound() {
        // given
        saveDTO.setId(999L);
        when(spuMapper.selectById(999L)).thenReturn(null);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> spuService.updateSpu(saveDTO));

        assertEquals(40004, exception.getCode());
    }

    @Test
    @DisplayName("上架商品 - 成功")
    void onSale_success() {
        // given
        testSpu.setStatus(0);  // 下架状态
        when(spuMapper.selectById(1L)).thenReturn(testSpu);
        when(spuMapper.updateById(any(Spu.class))).thenReturn(1);

        // when
        assertDoesNotThrow(() -> spuService.onSale(1L));

        // then
        verify(spuMapper, times(1)).updateById(any(Spu.class));
    }

    @Test
    @DisplayName("上架商品 - 已上架不能重复上架")
    void onSale_alreadyOnSale() {
        // given
        testSpu.setStatus(1);  // 已上架状态
        when(spuMapper.selectById(1L)).thenReturn(testSpu);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> spuService.onSale(1L));

        assertEquals(40008, exception.getCode());
        assertEquals("商品已上架，不能重复上架", exception.getMessage());
    }

    @Test
    @DisplayName("下架商品 - 成功")
    void offSale_success() {
        // given
        testSpu.setStatus(1);  // 上架状态
        when(spuMapper.selectById(1L)).thenReturn(testSpu);
        when(spuMapper.updateById(any(Spu.class))).thenReturn(1);

        // when
        assertDoesNotThrow(() -> spuService.offSale(1L));

        // then
        verify(spuMapper, times(1)).updateById(any(Spu.class));
    }

    @Test
    @DisplayName("下架商品 - 已下架不能重复下架")
    void offSale_alreadyOffSale() {
        // given
        testSpu.setStatus(0);  // 已下架状态
        when(spuMapper.selectById(1L)).thenReturn(testSpu);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> spuService.offSale(1L));

        assertEquals(40007, exception.getCode());
        assertEquals("商品已下架，不能重复下架", exception.getMessage());
    }
}
