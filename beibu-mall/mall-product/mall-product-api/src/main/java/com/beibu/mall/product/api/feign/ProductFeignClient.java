package com.beibu.mall.product.api.feign;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.product.api.dto.SpuDetailVO;
import com.beibu.mall.product.api.dto.SkuVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 商品服务 Feign 客户端
 *
 * 什么是 Feign？
 * Feign 是 Spring Cloud 提供的声明式 HTTP 客户端。
 * 你只需要定义一个接口，Feign 会自动帮你发送 HTTP 请求。
 *
 * 为什么要用 Feign？
 * 在微服务架构中，服务之间需要相互调用。
 * 例如：订单服务需要查询商品信息，就需要调用商品服务的接口。
 * 用 Feign 可以像调用本地方法一样调用远程服务。
 *
 * @FeignClient 注解参数：
 * - name: 要调用的服务名称（在 Nacos 中注册的名称）
 * - path: URL 前缀
 *
 * 其他服务使用方式：
 * 1. 在 pom.xml 中依赖 mall-product-api
 * 2. 在启动类上添加 @EnableFeignClients
 * 3. 注入 ProductFeignClient 即可使用
 */
@FeignClient(name = "product-service", path = "/api/product")
public interface ProductFeignClient {

    /**
     * 查询商品详情（含 SKU 列表）
     *
     * 订单服务下单时需要调用此接口，获取商品价格、库存等信息。
     */
    @GetMapping("/spu/{id}")
    Result<SpuDetailVO> getSpuDetail(@PathVariable("id") Long spuId);

    /**
     * 查询 SPU 下的所有 SKU
     */
    @GetMapping("/sku/list")
    Result<List<SkuVO>> listSkuBySpuId(@RequestParam("spuId") Long spuId);
}
