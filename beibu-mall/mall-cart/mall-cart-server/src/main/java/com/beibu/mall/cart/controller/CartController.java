package com.beibu.mall.cart.controller;

import com.beibu.mall.cart.api.dto.CartDTO;
import com.beibu.mall.cart.api.dto.CartItemVO;
import com.beibu.mall.cart.service.CartService;
import com.beibu.mall.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车控制器
 *
 * 用户身份说明：
 * 这里用 @RequestHeader("X-User-Id") 获取用户 ID。
 * 实际项目中，用户 ID 通常从 JWT Token 中解析。
 * 网关会验证 Token 并把用户 ID 放到请求头中传给下游服务。
 *
 * TODO: 后续接入 JWT 认证后，改为从 Token 中解析用户 ID
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "购物车管理", description = "购物车的增删改查")
public class CartController {

    private final CartService cartService;

    /**
     * 加入购物车
     *
     * 前端调用示例：
     * POST /api/cart
     * Header: X-User-Id: 1001
     * Body: { "skuId": 10086, "quantity": 2 }
     */
    @PostMapping
    @Operation(summary = "加入购物车", description = "将商品添加到购物车，已有则累加数量")
    public Result<Void> addItem(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CartDTO dto) {
        cartService.addItem(userId, dto);
        return Result.ok();
    }

    /**
     * 修改购物车商品数量
     *
     * 前端调用示例：
     * PUT /api/cart
     * Header: X-User-Id: 1001
     * Body: { "skuId": 10086, "quantity": 5 }
     */
    @PutMapping
    @Operation(summary = "修改数量", description = "修改购物车中商品的数量")
    public Result<Void> updateQuantity(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CartDTO dto) {
        cartService.updateQuantity(userId, dto);
        return Result.ok();
    }

    /**
     * 删除购物车中的商品
     *
     * 前端调用示例：
     * DELETE /api/cart/10086
     * Header: X-User-Id: 1001
     */
    @DeleteMapping("/{skuId}")
    @Operation(summary = "删除商品", description = "从购物车中删除指定商品")
    public Result<Void> deleteItem(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long skuId) {
        cartService.deleteItem(userId, skuId);
        return Result.ok();
    }

    /**
     * 查看购物车列表
     *
     * 前端调用示例：
     * GET /api/cart/list
     * Header: X-User-Id: 1001
     *
     * 返回示例：
     * {
     *   "code": 200,
     *   "msg": "success",
     *   "data": [
     *     {
     *       "skuId": 10086,
     *       "quantity": 2,
     *       "productName": "北部湾大对虾",
     *       "spec": "鲜活/500g",
     *       "price": 89.00,
     *       "img": "https://xxx/shrimp.jpg"
     *     }
     *   ]
     * }
     */
    @GetMapping("/list")
    @Operation(summary = "购物车列表", description = "查看购物车所有商品（含商品详情）")
    public Result<List<CartItemVO>> listItems(
            @RequestHeader("X-User-Id") Long userId) {
        List<CartItemVO> items = cartService.listItems(userId);
        return Result.ok(items);
    }
}
