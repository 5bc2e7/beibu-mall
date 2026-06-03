package com.beibu.mall.inventory.api.feign;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.inventory.api.dto.StockOperationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 库存服务 Feign 客户端接口
 *
 * 什么是 Feign？
 * Feign 是一个"声明式 HTTP 客户端"。
 * 你只需要定义接口，Feign 会自动帮你发 HTTP 请求。
 * 就像调用本地方法一样调用远程服务。
 *
 * 工作原理：
 * 1. 你定义接口方法 + 注解（@GetMapping, @PostMapping 等）
 * 2. Feign 根据注解生成 HTTP 请求
 * 3. 通过服务发现（Nacos）找到目标服务的地址
 * 4. 发送 HTTP 请求并解析响应
 *
 * @FeignClient 注解参数：
 * - name = "inventory-service"：目标服务在 Nacos 注册的服务名
 * - path = "/api/inventory"：接口路径前缀
 * - fallbackFactory = ...：降级工厂，服务不可用时的兜底处理
 */
@FeignClient(name = "inventory-service", path = "/api/inventory",
    fallbackFactory = InventoryFeignClientFallbackFactory.class)
public interface InventoryFeignClient {

    /**
     * 预占库存（冻结库存）
     *
     * 使用场景：用户下单但还没支付时，先把库存冻结起来
     * 防止这段时间别人把库存买走
     *
     * @param stockOperationDTO 库存操作请求（包含 skuId、数量、订单ID）
     * @return Result<Void> 统一返回结果，code=200 表示成功
     */
    @PostMapping("/occupy")
    Result<Void> occupyStock(@RequestBody StockOperationDTO stockOperationDTO);

    /**
     * 释放预占库存（解冻库存）
     *
     * 使用场景：用户取消订单或支付超时时，把冻结的库存还回去
     *
     * @param stockOperationDTO 库存操作请求
     * @return Result<Void> 统一返回结果
     */
    @PostMapping("/release")
    Result<Void> releaseStock(@RequestBody StockOperationDTO stockOperationDTO);

    /**
     * 确认扣减（从预占转为真正扣减）
     *
     * 使用场景：用户支付成功后，调用这个方法
     * 把预占的库存真正扣掉
     *
     * @param stockOperationDTO 库存操作请求
     * @return Result<Void> 统一返回结果
     */
    @PostMapping("/confirm")
    Result<Void> confirmDeduct(@RequestBody StockOperationDTO stockOperationDTO);
}
