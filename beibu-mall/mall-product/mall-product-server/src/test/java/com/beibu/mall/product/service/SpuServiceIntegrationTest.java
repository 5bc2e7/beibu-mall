package com.beibu.mall.product.service;

import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.product.api.dto.SpuDetailVO;
import com.beibu.mall.product.api.dto.SpuSaveDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SpuServiceIntegrationTest {

    @Autowired
    private SpuService spuService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private SpuSaveDTO saveDTO;

    @BeforeEach
    void setUp() {
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

    @Test
    @DisplayName("集成测试：添加商品并查询详情")
    void addSpu_and_getDetail() {
        // 1. 添加商品
        Long spuId = spuService.addSpu(saveDTO);
        assertNotNull(spuId);
        assertTrue(spuId > 0);

        // 2. 查询商品详情
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
        // 1. 添加商品（默认下架）
        Long spuId = spuService.addSpu(saveDTO);

        // 2. 上架商品
        spuService.onSale(spuId);
        SpuDetailVO detail1 = spuService.getSpuDetail(spuId);
        assertEquals(1, detail1.getStatus());

        // 3. 下架商品
        spuService.offSale(spuId);
        // @Transactional 导致 afterCommit 回调不触发，手动清除缓存
        redisTemplate.delete("product:spu:detail:" + spuId);
        SpuDetailVO detail2 = spuService.getSpuDetail(spuId);
        assertEquals(0, detail2.getStatus());
    }

    @Test
    @DisplayName("集成测试：修改商品")
    void updateSpu() {
        // 1. 添加商品
        Long spuId = spuService.addSpu(saveDTO);

        // 2. 修改商品
        saveDTO.setId(spuId);
        saveDTO.setName("修改后的商品名");
        saveDTO.setOrigin("修改后的产地");
        spuService.updateSpu(saveDTO);

        // 3. 查询验证
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
        spuService.onSale(spuId);
        assertThrows(BizException.class, () -> spuService.onSale(spuId));
    }

    @Test
    @DisplayName("集成测试：重复下架抛出异常")
    void offSale_alreadyOffSale() {
        Long spuId = spuService.addSpu(saveDTO);
        assertThrows(BizException.class, () -> spuService.offSale(spuId));
    }
}
