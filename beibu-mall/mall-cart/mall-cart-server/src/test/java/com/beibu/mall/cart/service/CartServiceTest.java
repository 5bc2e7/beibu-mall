package com.beibu.mall.cart.service;

import com.beibu.mall.cart.api.dto.CartDTO;
import com.beibu.mall.cart.api.dto.CartItemVO;
import com.beibu.mall.cart.service.impl.CartServiceImpl;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.common.result.Result;
import com.beibu.mall.product.api.dto.SkuVO;
import com.beibu.mall.product.api.feign.ProductFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 购物车服务单元测试
 *
 * 测试思路：
 * - Mock 掉 Redis 和 Feign（外部依赖），只测 Service 层逻辑
 * - 验证：正确调用了 Redis 命令、正确处理了异常情况
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ProductFeignClient productFeignClient;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private CartServiceImpl cartService;

    private static final Long USER_ID = 1001L;
    private static final Long SKU_ID = 10086L;
    private static final String CART_KEY = "cart:" + USER_ID;

    @BeforeEach
    void setUp() {
        // 让 redisTemplate.opsForHash() 返回我们的 mock 对象
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    // ========== 加入购物车 ==========

    @Test
    @DisplayName("加入购物车 - 新商品，直接递增")
    void addItem_newItem() {
        CartDTO dto = new CartDTO();
        dto.setSkuId(SKU_ID);
        dto.setQuantity(2);

        // 无论 key 是否存在，都用 HINCRBY 原子递增
        cartService.addItem(USER_ID, dto);

        verify(hashOperations).increment(CART_KEY, SKU_ID.toString(), 2);
        verify(hashOperations, never()).put(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("加入购物车 - 已有商品，累加数量")
    void addItem_existingItem() {
        CartDTO dto = new CartDTO();
        dto.setSkuId(SKU_ID);
        dto.setQuantity(3);

        // HINCRBY 在原值上累加，无需先查再改
        cartService.addItem(USER_ID, dto);

        verify(hashOperations).increment(CART_KEY, SKU_ID.toString(), 3);
    }

    @Test
    @DisplayName("加入购物车 - 最小数量 1")
    void addItem_minQuantity() {
        CartDTO dto = new CartDTO();
        dto.setSkuId(SKU_ID);
        dto.setQuantity(1);

        cartService.addItem(USER_ID, dto);

        verify(hashOperations).increment(CART_KEY, SKU_ID.toString(), 1);
    }

    @Test
    @DisplayName("加入购物车 - 不同 SKU 互不影响")
    void addItem_multipleSkus() {
        Long skuId2 = 10087L;

        CartDTO dto1 = new CartDTO();
        dto1.setSkuId(SKU_ID);
        dto1.setQuantity(2);

        CartDTO dto2 = new CartDTO();
        dto2.setSkuId(skuId2);
        dto2.setQuantity(5);

        cartService.addItem(USER_ID, dto1);
        cartService.addItem(USER_ID, dto2);

        verify(hashOperations).increment(CART_KEY, SKU_ID.toString(), 2);
        verify(hashOperations).increment(CART_KEY, skuId2.toString(), 5);
    }

    // ========== 修改数量 ==========

    @Test
    @DisplayName("修改数量 - 成功")
    void updateQuantity_success() {
        CartDTO dto = new CartDTO();
        dto.setSkuId(SKU_ID);
        dto.setQuantity(5);

        when(hashOperations.hasKey(CART_KEY, SKU_ID.toString())).thenReturn(true);

        cartService.updateQuantity(USER_ID, dto);

        // 验证：调用了 HSET 命令覆盖数量
        verify(hashOperations).put(CART_KEY, SKU_ID.toString(), 5);
    }

    @Test
    @DisplayName("修改数量 - 商品不在购物车中")
    void updateQuantity_notInCart() {
        CartDTO dto = new CartDTO();
        dto.setSkuId(SKU_ID);
        dto.setQuantity(5);

        when(hashOperations.hasKey(CART_KEY, SKU_ID.toString())).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> cartService.updateQuantity(USER_ID, dto));

        assertEquals(40021, ex.getCode());
        assertEquals("购物车中不存在该商品", ex.getMessage());
    }

    // ========== 删除商品 ==========

    @Test
    @DisplayName("删除商品 - 成功")
    void deleteItem_success() {
        when(hashOperations.delete(CART_KEY, SKU_ID.toString())).thenReturn(1L);

        cartService.deleteItem(USER_ID, SKU_ID);

        // 验证：调用了 HDEL 命令
        verify(hashOperations).delete(CART_KEY, SKU_ID.toString());
    }

    @Test
    @DisplayName("删除商品 - 商品不在购物车中")
    void deleteItem_notInCart() {
        when(hashOperations.delete(CART_KEY, SKU_ID.toString())).thenReturn(0L);

        BizException ex = assertThrows(BizException.class,
                () -> cartService.deleteItem(USER_ID, SKU_ID));

        assertEquals(40022, ex.getCode());
        assertEquals("购物车中不存在该商品", ex.getMessage());
    }

    // ========== 查看购物车列表 ==========

    @Test
    @DisplayName("查看购物车 - 空购物车")
    void listItems_emptyCart() {
        when(hashOperations.entries(CART_KEY)).thenReturn(Collections.emptyMap());

        List<CartItemVO> items = cartService.listItems(USER_ID);

        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    @Test
    @DisplayName("查看购物车 - 有商品，Feign 调用成功")
    void listItems_withItems() {
        // 购物车中有 1 个 SKU
        Map<Object, Object> entries = new HashMap<>();
        entries.put(SKU_ID.toString(), 2);
        when(hashOperations.entries(CART_KEY)).thenReturn(entries);

        // Mock 商品服务返回
        SkuVO skuVO = new SkuVO();
        skuVO.setId(SKU_ID);
        skuVO.setSpec("鲜活/500g");
        skuVO.setPrice(new BigDecimal("89.00"));
        skuVO.setImg("http://img.test/shrimp.jpg");
        when(productFeignClient.getSkuById(SKU_ID)).thenReturn(Result.ok(skuVO));

        List<CartItemVO> items = cartService.listItems(USER_ID);

        assertEquals(1, items.size());
        CartItemVO item = items.get(0);
        assertEquals(SKU_ID, item.getSkuId());
        assertEquals(2, item.getQuantity());
        assertEquals("鲜活/500g", item.getSpec());
        assertEquals(new BigDecimal("89.00"), item.getPrice());
    }

    @Test
    @DisplayName("查看购物车 - Feign 调用失败，商品信息为空")
    void listItems_feignFailure() {
        Map<Object, Object> entries = new HashMap<>();
        entries.put(SKU_ID.toString(), 1);
        when(hashOperations.entries(CART_KEY)).thenReturn(entries);

        // Feign 调用抛异常
        when(productFeignClient.getSkuById(SKU_ID)).thenThrow(new RuntimeException("服务不可用"));

        List<CartItemVO> items = cartService.listItems(USER_ID);

        assertEquals(1, items.size());
        // 商品信息应标记为失效
        assertEquals("商品已失效", items.get(0).getProductName());
    }
}
