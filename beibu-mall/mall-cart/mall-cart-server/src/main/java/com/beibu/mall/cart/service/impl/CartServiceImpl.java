package com.beibu.mall.cart.service.impl;

import com.beibu.mall.cart.api.dto.CartDTO;
import com.beibu.mall.cart.api.dto.CartItemVO;
import com.beibu.mall.cart.service.CartService;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.common.result.Result;
import com.beibu.mall.product.api.dto.SkuVO;
import com.beibu.mall.product.api.feign.ProductFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 购物车服务实现类
 *
 * 核心思路：
 * 购物车数据存在 Redis 的 Hash 结构中。
 *
 * Redis Hash 结构示意：
 * Key: cart:1001（用户ID=1001 的购物车）
 * ┌────────────┬─────────────┐
 * │  Hash Key  │ Hash Value  │
 * ├────────────┼─────────────┤
 * │ "10086"    │ 2           │  ← SKU ID=10086，数量=2
 * │ "10087"    │ 1           │  ← SKU ID=10087，数量=1
 * │ "10090"    │ 3           │  ← SKU ID=10090，数量=3
 * └────────────┴─────────────┘
 *
 * 为什么用 Hash 而不是 String？
 * - String 存整个购物车：改一个商品数量，要读整个 JSON → 改 → 写回去（并发不安全）
 * - Hash 存每个商品：改一个商品数量，直接 HINCRBY 一条命令搞定（原子操作，线程安全）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductFeignClient productFeignClient;

    /**
     * Redis key 前缀
     * 每个用户的购物车 key 格式：cart:用户ID
     */
    private static final String CART_KEY_PREFIX = "cart:";

    @Override
    public void addItem(Long userId, CartDTO dto) {
        String key = CART_KEY_PREFIX + userId;
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();

        // 直接用 HINCRBY（原子递增），不管 key 是否存在：
        // - key 不存在 → Redis 自动创建，从 0 开始递增
        // - key 已存在 → 在原值上累加
        // 一条命令搞定，没有竞态条件
        hashOps.increment(key, dto.getSkuId().toString(), dto.getQuantity());
        log.info("用户 {} 购物车 SKU {} 数量 +{}", userId, dto.getSkuId(), dto.getQuantity());
    }

    @Override
    public void updateQuantity(Long userId, CartDTO dto) {
        String key = CART_KEY_PREFIX + userId;
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();

        // 检查商品是否在购物车中
        if (!hashOps.hasKey(key, dto.getSkuId().toString())) {
            throw new BizException(40021, "购物车中不存在该商品");
        }

        // 直接覆盖数量
        hashOps.put(key, dto.getSkuId().toString(), dto.getQuantity());
        log.info("用户 {} 购物车 SKU {} 数量修改为 {}", userId, dto.getSkuId(), dto.getQuantity());
    }

    @Override
    public void deleteItem(Long userId, Long skuId) {
        String key = CART_KEY_PREFIX + userId;
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();

        // HDEL 命令：删除 Hash 中的指定 field
        Long deleted = hashOps.delete(key, skuId.toString());
        if (deleted == null || deleted == 0) {
            throw new BizException(40022, "购物车中不存在该商品");
        }
        log.info("用户 {} 购物车删除 SKU {}", userId, skuId);
    }

    @Override
    public List<CartItemVO> listItems(Long userId) {
        String key = CART_KEY_PREFIX + userId;
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();

        // HGETALL：获取 Hash 中所有的 field-value 对
        Map<Object, Object> entries = hashOps.entries(key);
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集所有 SKU ID
        List<Long> skuIds = new ArrayList<>();
        for (Object hashKey : entries.keySet()) {
            skuIds.add(Long.parseLong(hashKey.toString()));
        }

        // 调用商品服务，批量查询 SKU 信息
        // 这就是 OpenFeign 的用法：像调用本地方法一样调用远程服务
        Map<Long, SkuVO> skuInfoMap = fetchSkuInfo(skuIds);

        // 组装购物车列表
        List<CartItemVO> cartItems = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            Long skuId = Long.parseLong(entry.getKey().toString());
            // RedisConfig 启用了 activateDefaultTyping，反序列化出来已经是 Integer，直接强转
            Integer quantity = (Integer) entry.getValue();

            CartItemVO vo = new CartItemVO();
            vo.setSkuId(skuId);
            vo.setQuantity(quantity);

            // 从商品服务返回的数据中补全商品信息
            SkuVO skuInfo = skuInfoMap.get(skuId);
            if (skuInfo != null) {
                vo.setProductName(skuInfo.getSpec()); // 用 spec 作为商品名展示
                vo.setSpec(skuInfo.getSpec());
                vo.setPrice(skuInfo.getPrice());
                vo.setImg(skuInfo.getImg());
            } else {
                // 商品服务查不到（可能已下架），标记为"商品已失效"
                vo.setProductName("商品已失效");
                vo.setSpec("--");
                vo.setPrice(null);
                vo.setImg(null);
            }

            cartItems.add(vo);
        }

        return cartItems;
    }

    /**
     * 调用商品服务查询 SKU 信息
     *
     * 这里演示了 OpenFeign 的实际用法：
     * 1. 注入 ProductFeignClient（在 mall-product-api 模块中定义的接口）
     * 2. 直接调用接口方法，Feign 自动发 HTTP 请求到商品服务
     * 3. 返回值自动反序列化成 Java 对象
     *
     * 如果商品服务挂了怎么办？
     * ProductFeignClient 配置了 FallbackFactory（降级工厂），
     * 调用失败时会返回 Result.fail()，不会让整个请求崩溃。
     */
    private Map<Long, SkuVO> fetchSkuInfo(List<Long> skuIds) {
        Map<Long, SkuVO> resultMap = new HashMap<>();

        for (Long skuId : skuIds) {
            try {
                // 调用商品服务的 Feign 客户端
                // 实际发的 HTTP 请求：GET http://product-service/api/product/sku/{skuId}
                // 但你只需要写：productFeignClient.getSkuById(skuId)
                Result<SkuVO> result = productFeignClient.getSkuById(skuId);
                if (result != null && result.getCode() == 200 && result.getData() != null) {
                    resultMap.put(skuId, result.getData());
                }
            } catch (Exception e) {
                // Feign 调用失败时记录日志，不影响购物车列表返回
                log.warn("调用商品服务查询 SKU {} 失败: {}", skuId, e.getMessage());
            }
        }

        return resultMap;
    }
}
