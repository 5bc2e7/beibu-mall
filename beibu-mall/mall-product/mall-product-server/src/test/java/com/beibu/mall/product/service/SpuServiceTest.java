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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private com.beibu.mall.product.mq.ProductSyncProducer productSyncProducer;

    @Mock
    private RLock rLock;

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

    /**
     * 配置 Redis Mock（只需要 delete 操作的测试调用这个）
     */
    private void setupRedisMocks() {
        when(redisTemplate.delete(anyString())).thenReturn(true);
    }

    /**
     * 配置 Redis ValueOperations Mock（缓存命中场景调用这个）
     */
    private void setupRedisValueMocks() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * 配置 Redis + Redisson Mock（缓存未命中需要重建缓存的场景调用这个）
     */
    private void setupCacheMocks() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        } catch (InterruptedException e) {
            // Mock 不会真正抛出异常
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
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
    @DisplayName("商品详情 - 缓存未命中，查数据库")
    void getSpuDetail_cacheMiss() {
        // given: 配置缓存 Mock，缓存未命中
        setupCacheMocks();
        when(valueOperations.get(anyString())).thenReturn(null);
        when(spuMapper.selectById(1L)).thenReturn(testSpu);
        when(skuService.listSkuBySpuId(1L)).thenReturn(java.util.Arrays.asList(new com.beibu.mall.product.api.dto.SkuVO()));

        // when
        SpuDetailVO detail = spuService.getSpuDetail(1L);

        // then: 查到了数据
        assertNotNull(detail);
        assertEquals(1L, detail.getId());
        assertEquals("北部湾大对虾", detail.getName());
        assertEquals("广西北海", detail.getOrigin());
        assertNotNull(detail.getSkuList());
        assertEquals(1, detail.getSkuList().size());

        // 验证：查了数据库
        verify(spuMapper).selectById(1L);
        // 验证：数据写回了缓存
        verify(valueOperations).set(anyString(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("商品详情 - 缓存命中，不查数据库")
    void getSpuDetail_cacheHit() {
        // given: 缓存命中，只需要 ValueOperations
        setupRedisValueMocks();
        SpuDetailVO cachedVO = new SpuDetailVO();
        cachedVO.setId(1L);
        cachedVO.setName("北部湾大对虾");
        when(valueOperations.get(anyString())).thenReturn(cachedVO);

        // when
        SpuDetailVO detail = spuService.getSpuDetail(1L);

        // then: 返回缓存的数据
        assertNotNull(detail);
        assertEquals(1L, detail.getId());
        assertEquals("北部湾大对虾", detail.getName());

        // 验证：没有查数据库，没有加锁
        verify(spuMapper, never()).selectById(anyLong());
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    @DisplayName("商品详情 - 缓存穿透防护：查不到的数据也缓存空值")
    void getSpuDetail_cacheNullValue() {
        // given: 缓存未命中，数据库没有这个商品
        setupCacheMocks();
        when(valueOperations.get(anyString())).thenReturn(null);
        when(spuMapper.selectById(999L)).thenReturn(null);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> spuService.getSpuDetail(999L));

        assertEquals(40004, exception.getCode());

        // 验证：缓存了空值（防穿透）
        verify(valueOperations).set(eq("product:spu:detail:999"), any(), eq(2L), any());
    }

    @Test
    @DisplayName("商品详情 - 缓存命中空值标记，直接返回不存在")
    void getSpuDetail_cacheHitNullPlaceholder() {
        // given: 缓存命中的是空值标记 "__NULL__"
        setupRedisValueMocks();
        when(valueOperations.get(anyString())).thenReturn("__NULL__");

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> spuService.getSpuDetail(999L));

        assertEquals(40004, exception.getCode());

        // 验证：没有查数据库，没有加锁
        verify(spuMapper, never()).selectById(anyLong());
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    @DisplayName("商品详情 - 锁获取失败，降级查数据库并缓存")
    void getSpuDetail_lockFailed_degradeToDB() {
        // given: 缓存未命中，锁获取失败
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(false);
        } catch (InterruptedException e) {
            // Mock 不会真正抛出异常
        }

        when(valueOperations.get(anyString())).thenReturn(null);
        when(spuMapper.selectById(1L)).thenReturn(testSpu);
        when(skuService.listSkuBySpuId(1L)).thenReturn(java.util.Arrays.asList(new com.beibu.mall.product.api.dto.SkuVO()));

        // when: 降级路径
        SpuDetailVO detail = spuService.getSpuDetail(1L);

        // then: 查到了数据
        assertNotNull(detail);
        assertEquals(1L, detail.getId());
        assertEquals("北部湾大对虾", detail.getName());

        // 验证：查了数据库，数据写回了缓存
        verify(spuMapper).selectById(1L);
        verify(valueOperations).set(anyString(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("商品详情 - 锁被中断，降级查数据库并缓存")
    void getSpuDetail_lockInterrupted_degradeToDB() throws InterruptedException {
        // given: 缓存未命中，锁被中断
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any())).thenThrow(new InterruptedException("测试中断"));

        when(valueOperations.get(anyString())).thenReturn(null);
        when(spuMapper.selectById(1L)).thenReturn(testSpu);
        when(skuService.listSkuBySpuId(1L)).thenReturn(java.util.Arrays.asList(new com.beibu.mall.product.api.dto.SkuVO()));

        // when: 降级路径
        SpuDetailVO detail = spuService.getSpuDetail(1L);

        // then: 查到了数据
        assertNotNull(detail);
        assertEquals(1L, detail.getId());
        assertEquals("北部湾大对虾", detail.getName());

        // 验证：查了数据库，数据写回了缓存
        verify(spuMapper).selectById(1L);
        verify(valueOperations).set(anyString(), any(), anyLong(), any());

        // 验证：中断标志被恢复
        assertTrue(Thread.currentThread().isInterrupted());
        // 清除中断标志，避免影响其他测试
        Thread.interrupted();
    }

    @Test
    @DisplayName("商品详情 - 缓存数据损坏，删除脏数据并查数据库")
    void getSpuDetail_corruptedCache_fallbackToDB() {
        // given: 缓存命中的是脏数据（不是 SpuDetailVO 类型）
        setupCacheMocks();
        when(valueOperations.get(anyString())).thenReturn("这不是 SpuDetailVO 对象");
        when(spuMapper.selectById(1L)).thenReturn(testSpu);
        when(skuService.listSkuBySpuId(1L)).thenReturn(java.util.Arrays.asList(new com.beibu.mall.product.api.dto.SkuVO()));

        // when: 应该删掉脏数据，查数据库
        SpuDetailVO detail = spuService.getSpuDetail(1L);

        // then: 查到了数据
        assertNotNull(detail);
        assertEquals(1L, detail.getId());
        assertEquals("北部湾大对虾", detail.getName());

        // 验证：删除了脏数据（至少一次，可能两次：第一次删脏数据，第二次锁内又删）
        verify(redisTemplate, atLeastOnce()).delete("product:spu:detail:1");
        // 验证：查了数据库
        verify(spuMapper).selectById(1L);
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
    @DisplayName("修改商品 - 成功并清除缓存")
    void updateSpu_success() {
        setupRedisMocks();
        saveDTO.setId(1L);
        when(spuMapper.selectById(1L)).thenReturn(testSpu);
        when(spuMapper.updateById(any(Spu.class))).thenReturn(1);
        when(categoryMapper.selectById(6L)).thenReturn(new com.beibu.mall.product.entity.Category());

        assertDoesNotThrow(() -> spuService.updateSpu(saveDTO));

        verify(spuMapper, times(1)).updateById(any(Spu.class));
        verify(redisTemplate).delete("product:spu:detail:1");
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
    @DisplayName("上架商品 - 成功并清除缓存")
    void onSale_success() {
        setupRedisMocks();
        testSpu.setStatus(0);
        when(spuMapper.selectById(1L)).thenReturn(testSpu);
        when(spuMapper.updateById(any(Spu.class))).thenReturn(1);

        assertDoesNotThrow(() -> spuService.onSale(1L));

        verify(spuMapper, times(1)).updateById(any(Spu.class));
        verify(redisTemplate).delete("product:spu:detail:1");
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
    @DisplayName("下架商品 - 成功并清除缓存")
    void offSale_success() {
        setupRedisMocks();
        testSpu.setStatus(1);
        when(spuMapper.selectById(1L)).thenReturn(testSpu);
        when(spuMapper.updateById(any(Spu.class))).thenReturn(1);

        assertDoesNotThrow(() -> spuService.offSale(1L));

        verify(spuMapper, times(1)).updateById(any(Spu.class));
        verify(redisTemplate).delete("product:spu:detail:1");
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
