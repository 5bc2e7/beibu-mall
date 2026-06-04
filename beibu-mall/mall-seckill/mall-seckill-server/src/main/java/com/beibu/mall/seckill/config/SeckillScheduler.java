package com.beibu.mall.seckill.config;

import com.beibu.mall.seckill.entity.SeckillActivity;
import com.beibu.mall.seckill.mapper.SeckillActivityMapper;
import com.beibu.mall.seckill.service.SeckillService;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class SeckillScheduler {

    private final SeckillActivityMapper seckillActivityMapper;
    private final SeckillService seckillService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Scheduled(cron = "30 * * * * ?")
    public void autoWarmUpStock() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fiveMinutesLater = now.plusMinutes(5);

        List<SeckillActivity> activities = seckillActivityMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getStatus, 0)
                        .le(SeckillActivity::getStartTime, fiveMinutesLater)
                        .ge(SeckillActivity::getStartTime, now)
        );

        for (SeckillActivity activity : activities) {
            try {
                seckillService.warmUpStock(activity.getId());
                log.info("自动预热库存成功，活动ID: {}", activity.getId());
            } catch (Exception e) {
                log.error("自动预热库存失败，活动ID: {}", activity.getId(), e);
            }
        }
    }

    @Scheduled(cron = "0 * * * * ?")
    public void autoUpdateActivityStatus() {
        LocalDateTime now = LocalDateTime.now();

        int started = seckillActivityMapper.update(null,
                new UpdateWrapper<SeckillActivity>()
                        .eq("status", 0)
                        .le("start_time", now)
                        .set("status", 1)
        );
        if (started > 0) {
            log.info("自动更新活动状态：{} 个活动开始", started);
        }

        int ended = seckillActivityMapper.update(null,
                new UpdateWrapper<SeckillActivity>()
                        .eq("status", 1)
                        .le("end_time", now)
                        .set("status", 2)
        );
        if (ended > 0) {
            log.info("自动更新活动状态：{} 个活动结束", ended);
        }
    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void syncStock() {
        List<SeckillActivity> activities = seckillActivityMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getStatus, 1)
        );

        for (SeckillActivity activity : activities) {
            try {
                String stockKey = RedisKeyConstants.getStockKey(activity.getId());
                Boolean exists = redisTemplate.hasKey(stockKey);

                if (Boolean.FALSE.equals(exists)) {
                    log.warn("活动库存未预热，自动预热，活动ID: {}", activity.getId());
                    seckillService.warmUpStock(activity.getId());
                    continue;
                }

                Object redisStock = redisTemplate.opsForValue().get(stockKey);
                int redisStockNum = Integer.parseInt(redisStock.toString());
                int dbStock = activity.getAvailableStock();

                if (redisStockNum > dbStock) {
                    log.warn("Redis库存偏高，同步，活动ID: {}, Redis: {}, DB: {}", activity.getId(), redisStockNum, dbStock);
                    redisTemplate.opsForValue().set(stockKey, Math.max(0, dbStock));
                    log.info("已同步库存，活动ID: {}, 库存: {}", activity.getId(), Math.max(0, dbStock));
                } else if (redisStockNum < dbStock) {
                    log.info("Redis库存低于DB（可能有MQ在途），跳过同步，活动ID: {}, Redis: {}, DB: {}",
                            activity.getId(), redisStockNum, dbStock);
                }
            } catch (Exception e) {
                log.error("同步库存失败，活动ID: {}", activity.getId(), e);
            }
        }
    }
}
