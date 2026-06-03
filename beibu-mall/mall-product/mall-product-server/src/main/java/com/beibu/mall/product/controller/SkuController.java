package com.beibu.mall.product.controller;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.product.api.dto.SkuSaveDTO;
import com.beibu.mall.product.api.dto.SkuVO;
import com.beibu.mall.product.service.SkuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SKU 控制器
 *
 * TODO: 管理员接口（POST/PUT/DELETE）需要在网关层添加认证和权限校验
 * 与 SpuController 相同，生产环境需要 JWT Token 验证和角色权限校验。
 */
@RestController
@RequestMapping("/api/product/sku")
@RequiredArgsConstructor
@Tag(name = "SKU 管理", description = "商品规格的增删改查")
public class SkuController {

    private final SkuService skuService;

    @GetMapping("/{id}")
    @Operation(summary = "SKU 详情", description = "根据 SKU ID 查询单个 SKU 信息")
    public Result<SkuVO> getSkuById(@PathVariable Long id) {
        SkuVO sku = skuService.getSkuById(id);
        return Result.ok(sku);
    }

    @GetMapping("/list")
    @Operation(summary = "SKU 列表", description = "获取指定 SPU 下的所有 SKU")
    public Result<List<SkuVO>> listSkuBySpuId(@RequestParam Long spuId) {
        List<SkuVO> skuList = skuService.listSkuBySpuId(spuId);
        return Result.ok(skuList);
    }

    @PostMapping
    @Operation(summary = "添加 SKU", description = "添加新的 SKU（管理员）")
    public Result<Long> addSku(@Valid @RequestBody SkuSaveDTO dto) {
        Long skuId = skuService.addSku(dto);
        return Result.ok(skuId);
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改 SKU", description = "修改 SKU 信息（管理员）")
    public Result<Void> updateSku(@PathVariable Long id, @Valid @RequestBody SkuSaveDTO dto) {
        dto.setId(id);
        skuService.updateSku(dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 SKU", description = "删除 SKU（管理员）")
    public Result<Void> deleteSku(@PathVariable Long id) {
        skuService.deleteSku(id);
        return Result.ok();
    }
}
