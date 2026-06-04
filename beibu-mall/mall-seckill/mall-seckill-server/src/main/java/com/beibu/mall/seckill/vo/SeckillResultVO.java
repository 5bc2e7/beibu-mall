package com.beibu.mall.seckill.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀结果 VO（View Object，视图对象）
 *
 * VO = 返回给前端显示用的对象
 * 和 DTO 的区别：DTO 是接收前端数据的，VO 是返回给前端的
 *
 * @Builder：建造者模式，让创建对象更优雅
 * 比如：SeckillResultVO.builder().token("xxx").success(true).build()
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否抢购成功
     * true = 抢到了
     * false = 没抢到（库存不足 或 已经抢过了）
     */
    private Boolean success;

    /**
     * 查询令牌
     * 抢购成功后返回，前端用这个 token 来查询最终结果
     * 为什么需要这个？因为秒杀是异步的，用户抢到后还要等 MQ 消费者写数据库
     */
    private String token;

    /**
     * 提示信息
     * 比如："抢购成功，请稍后查询结果" 或 "库存已售罄"
     */
    private String message;

    /**
     * 活动ID
     */
    private Long activityId;

    /**
     * 商品名称
     */
    private String productName;

    /**
     * 秒杀价格
     */
    private BigDecimal seckillPrice;
}
