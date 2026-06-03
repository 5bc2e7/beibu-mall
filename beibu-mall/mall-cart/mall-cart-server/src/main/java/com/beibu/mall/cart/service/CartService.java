package com.beibu.mall.cart.service;

import com.beibu.mall.cart.api.dto.CartDTO;
import com.beibu.mall.cart.api.dto.CartItemVO;

import java.util.List;

/**
 * 购物车服务接口
 *
 * 为什么分 interface 和 impl？
 * 这是 Spring 的设计模式：面向接口编程。
 * - interface 定义"能做什么"（契约）
 * - impl 定义"怎么做"（实现）
 *
 * 好处：
 * 1. 如果以后要换实现（比如从 Redis 换成 Memcached），只改 impl，不动 controller
 * 2. 方便单元测试：测试 controller 时可以用 mock 的 service
 */
public interface CartService {

    /**
     * 加入购物车
     * 如果该 SKU 已在购物车中，则累加数量
     *
     * @param userId 用户 ID（从登录态获取）
     * @param dto    购物车操作参数（SKU ID + 数量）
     */
    void addItem(Long userId, CartDTO dto);

    /**
     * 修改购物车商品数量
     *
     * @param userId 用户 ID
     * @param dto    购物车操作参数（SKU ID + 新数量）
     */
    void updateQuantity(Long userId, CartDTO dto);

    /**
     * 删除购物车中的商品
     *
     * @param userId 用户 ID
     * @param skuId  要删除的 SKU ID
     */
    void deleteItem(Long userId, Long skuId);

    /**
     * 查看购物车列表
     * 会调用商品服务补全商品名称、图片、价格等信息
     *
     * @param userId 用户 ID
     * @return 购物车项列表（包含商品详细信息）
     */
    List<CartItemVO> listItems(Long userId);
}
