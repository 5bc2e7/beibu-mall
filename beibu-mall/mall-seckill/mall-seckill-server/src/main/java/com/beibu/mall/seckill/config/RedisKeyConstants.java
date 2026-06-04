package com.beibu.mall.seckill.config;

/**
 * Redis Key 常量类
 *
 * 把所有 Redis key 的前缀定义在这里，避免到处写魔法字符串
 * 魔法字符串 = 代码里直接写 "seckill:stock:" 这种，不容易维护
 *
 * 命名规范：模块:业务:标识
 * 比如：seckill:stock:1001 表示秒杀模块、库存业务、活动ID 1001
 */
public class RedisKeyConstants {

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    public static final String SECKILL_BOUGHT_KEY = "seckill:bought:";

    public static final String SECKILL_RESULT_KEY = "seckill:result:";

    public static final long RESULT_TTL_HOURS = 24;

    /**
     * 获取库存 key
     *
     * @param activityId 活动ID
     * @return 完整的 Redis key
     */
    public static String getStockKey(Long activityId) {
        return SECKILL_STOCK_KEY + activityId;
    }

    /**
     * 获取已购用户 key
     *
     * @param activityId 活动ID
     * @return 完整的 Redis key
     */
    public static String getBoughtKey(Long activityId) {
        return SECKILL_BOUGHT_KEY + activityId;
    }

    /**
     * 获取秒杀结果 key
     *
     * @param token 查询令牌
     * @return 完整的 Redis key
     */
    public static String getResultKey(String token) {
        return SECKILL_RESULT_KEY + token;
    }
}
