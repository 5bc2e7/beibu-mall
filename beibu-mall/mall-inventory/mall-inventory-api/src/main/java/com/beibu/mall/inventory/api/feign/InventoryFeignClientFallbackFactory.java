package com.beibu.mall.inventory.api.feign;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.inventory.api.dto.StockOperationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * InventoryFeignClient 降级工厂
 *
 * 什么是降级？
 * 当库存服务不可用时（如网络故障、服务宕机），调用方会收到错误。
 * 降级提供一个备选方案，让调用方可以继续运行，而不是直接报错。
 *
 * 为什么需要降级？
 * 微服务架构中，服务间依赖复杂。如果库存服务挂了：
 * - 没有降级：订单服务也跟着挂（级联故障，雪崩效应）
 * - 有降级：订单服务返回友好提示，用户可以稍后重试
 */
@Slf4j
@Component
public class InventoryFeignClientFallbackFactory implements FallbackFactory<InventoryFeignClient> {

    @Override
    public InventoryFeignClient create(Throwable cause) {
        // 记录降级原因，方便排查问题
        log.error("库存服务降级，原因：{}", cause.getMessage());

        return new InventoryFeignClient() {
            @Override
            public Result<Void> occupyStock(StockOperationDTO stockOperationDTO) {
                return Result.fail(503, "库存服务暂时不可用，请稍后重试");
            }

            @Override
            public Result<Void> releaseStock(StockOperationDTO stockOperationDTO) {
                return Result.fail(503, "库存服务暂时不可用，请稍后重试");
            }

            @Override
            public Result<Void> confirmDeduct(StockOperationDTO stockOperationDTO) {
                return Result.fail(503, "库存服务暂时不可用，请稍后重试");
            }
        };
    }
}
