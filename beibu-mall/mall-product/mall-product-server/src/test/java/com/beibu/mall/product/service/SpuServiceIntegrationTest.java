package com.beibu.mall.product.service;

import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.product.api.dto.SpuDetailVO;
import com.beibu.mall.product.api.dto.SpuSaveDTO;
import com.beibu.mall.product.mapper.SpuMapper;
import com.beibu.mall.product.mq.ProductSyncProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SpuServiceIntegrationTest {

    @Autowired
    private SpuService spuService;

    @Autowired
    private SpuMapper spuMapper;

    @MockitoBean
    private ProductSyncProducer productSyncProducer;

    private SpuSaveDTO saveDTO;

    private final List<Long> createdSpuIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        createdSpuIds.clear();

        saveDTO = new SpuSaveDTO();
        saveDTO.setCategoryId(6L);
        saveDTO.setName("集成测试商品");
        saveDTO.setOrigin("测试产地");
        saveDTO.setIsFresh(1);
        saveDTO.setPriceType(1);
        saveDTO.setShelfLife(24);
        saveDTO.setMinBuy(1);
        saveDTO.setDescription("集成测试描述");
    }

    @AfterEach
    void tearDown() {
        for (Long spuId : createdSpuIds) {
            try {
                spuMapper.deleteById(spuId);
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("集成测试：添加商品并查询详情")
    void addSpu_and_getDetail() {
        Long spuId = spuService.addSpu(saveDTO);
        createdSpuIds.add(spuId);
        assertNotNull(spuId);
        assertTrue(spuId > 0);

        SpuDetailVO detail = spuService.getSpuDetail(spuId);
        assertNotNull(detail);
        assertEquals(spuId, detail.getId());
        assertEquals("集成测试商品", detail.getName());
        assertEquals("测试产地", detail.getOrigin());
        assertEquals(1, detail.getIsFresh());
        assertEquals(1, detail.getPriceType());
        assertEquals(24, detail.getShelfLife());
        assertEquals(1, detail.getMinBuy());
        assertEquals("集成测试描述", detail.getDescription());
        assertEquals(0, detail.getStatus());
    }

    @Test
    @DisplayName("集成测试：上架和下架商品")
    void onSale_and_offSale() {
        Long spuId = spuService.addSpu(saveDTO);
        createdSpuIds.add(spuId);

        spuService.onSale(spuId);
        SpuDetailVO detail1 = spuService.getSpuDetail(spuId);
        assertEquals(1, detail1.getStatus());

        spuService.offSale(spuId);
        SpuDetailVO detail2 = spuService.getSpuDetail(spuId);
        assertEquals(0, detail2.getStatus());
    }

    @Test
    @DisplayName("集成测试：修改商品")
    void updateSpu() {
        Long spuId = spuService.addSpu(saveDTO);
        createdSpuIds.add(spuId);

        saveDTO.setId(spuId);
        saveDTO.setName("修改后的商品名");
        saveDTO.setOrigin("修改后的产地");
        spuService.updateSpu(saveDTO);

        SpuDetailVO detail = spuService.getSpuDetail(spuId);
        assertEquals("修改后的商品名", detail.getName());
        assertEquals("修改后的产地", detail.getOrigin());
    }

    @Test
    @DisplayName("集成测试：商品不存在时抛出异常")
    void getSpuDetail_notFound() {
        assertThrows(Exception.class, () -> spuService.getSpuDetail(999999L));
    }

    @Test
    @DisplayName("集成测试：添加商品时分类不存在抛出异常")
    void addSpu_categoryNotFound() {
        saveDTO.setCategoryId(999999L);
        assertThrows(BizException.class, () -> spuService.addSpu(saveDTO));
    }

    @Test
    @DisplayName("集成测试：重复上架抛出异常")
    void onSale_alreadyOnSale() {
        Long spuId = spuService.addSpu(saveDTO);
        createdSpuIds.add(spuId);
        spuService.onSale(spuId);
        assertThrows(BizException.class, () -> spuService.onSale(spuId));
    }

    @Test
    @DisplayName("集成测试：重复下架抛出异常")
    void offSale_alreadyOffSale() {
        Long spuId = spuService.addSpu(saveDTO);
        createdSpuIds.add(spuId);
        assertThrows(BizException.class, () -> spuService.offSale(spuId));
    }
}
