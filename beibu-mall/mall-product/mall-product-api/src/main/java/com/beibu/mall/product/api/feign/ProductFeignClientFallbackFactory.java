package com.beibu.mall.product.api.feign;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.product.api.dto.SpuDetailVO;
import com.beibu.mall.product.api.dto.SkuVO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * ProductFeignClient 降级工厂
 *
 * 什么是降级？
 * 当商品服务不可用时（如网络故障、服务宕机），调用方会收到错误。
 * 降级提供一个备选方案，让调用方可以继续运行，而不是直接报错。
 *
 * 例如：订单服务查询商品详情失败时，返回一个空对象或默认值，
 * 而不是让整个下单流程失败。
 */
@Component
public class ProductFeignClientFallbackFactory implements FallbackFactory<ProductFeignClient> {

    @Override
    public ProductFeignClient create(Throwable cause) {
        return new ProductFeignClient() {
            @Override
            public Result<SpuDetailVO> getSpuDetail(Long spuId) {
                return Result.fail(500, "商品服务暂时不可用，请稍后重试");
            }

            @Override
            public Result<SkuVO> getSkuById(Long skuId) {
                return Result.fail(500, "商品服务暂时不可用，请稍后重试");
            }

            @Override
            public Result<List<SkuVO>> listSkuBySpuId(Long spuId) {
                return Result.fail(500, "商品服务暂时不可用，请稍后重试");
            }
        };
    }
}
