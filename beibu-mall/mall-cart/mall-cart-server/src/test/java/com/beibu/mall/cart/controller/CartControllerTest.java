package com.beibu.mall.cart.controller;

import com.beibu.mall.cart.api.dto.CartDTO;
import com.beibu.mall.cart.api.dto.CartItemVO;
import com.beibu.mall.cart.service.CartService;
import com.beibu.mall.common.result.Result;
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
import static org.mockito.Mockito.*;

/**
 * 购物车控制器测试
 *
 * 测试 CartController 的所有端点
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("购物车控制器测试")
class CartControllerTest {

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartController cartController;

    private Long userId;
    private CartDTO cartDTO;

    @BeforeEach
    void setUp() {
        userId = 1001L;
        cartDTO = new CartDTO();
        cartDTO.setSkuId(10086L);
        cartDTO.setQuantity(2);
    }

    @Test
    @DisplayName("添加购物车 - 成功")
    void addItem_success() {
        // given
        doNothing().when(cartService).addItem(userId, cartDTO);

        // when
        Result<Void> result = cartController.addItem(userId, cartDTO);

        // then
        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(cartService, times(1)).addItem(userId, cartDTO);
    }

    @Test
    @DisplayName("修改购物车数量 - 成功")
    void updateQuantity_success() {
        // given
        doNothing().when(cartService).updateQuantity(userId, cartDTO);

        // when
        Result<Void> result = cartController.updateQuantity(userId, cartDTO);

        // then
        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(cartService, times(1)).updateQuantity(userId, cartDTO);
    }

    @Test
    @DisplayName("删除购物车商品 - 成功")
    void deleteItem_success() {
        // given
        Long skuId = 10086L;
        doNothing().when(cartService).deleteItem(userId, skuId);

        // when
        Result<Void> result = cartController.deleteItem(userId, skuId);

        // then
        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(cartService, times(1)).deleteItem(userId, skuId);
    }

    @Test
    @DisplayName("查询购物车列表 - 成功")
    void listItems_success() {
        // given
        CartItemVO item1 = new CartItemVO();
        item1.setSkuId(10086L);
        item1.setQuantity(2);
        item1.setProductName("北部湾大对虾");
        item1.setPrice(new BigDecimal("89.00"));

        CartItemVO item2 = new CartItemVO();
        item2.setSkuId(10087L);
        item2.setQuantity(1);
        item2.setProductName("北海鱿鱼");
        item2.setPrice(new BigDecimal("69.00"));

        when(cartService.listItems(userId)).thenReturn(Arrays.asList(item1, item2));

        // when
        Result<List<CartItemVO>> result = cartController.listItems(userId);

        // then
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().size());
        assertEquals(10086L, result.getData().get(0).getSkuId());
        assertEquals(10087L, result.getData().get(1).getSkuId());
        verify(cartService, times(1)).listItems(userId);
    }
}
