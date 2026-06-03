package com.beibu.mall.product.controller;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.product.api.dto.PageResult;
import com.beibu.mall.product.api.dto.SpuDetailVO;
import com.beibu.mall.product.api.dto.SpuQueryDTO;
import com.beibu.mall.product.api.dto.SpuSaveDTO;
import com.beibu.mall.product.api.dto.SpuVO;
import com.beibu.mall.product.service.SpuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * SPU 控制器
 *
 * SPU = Standard Product Unit（标准产品单元）
 * 负责商品的增删改查和上下架操作。
 *
 * TODO: 管理员接口（POST/PUT/DELETE）需要在网关层添加认证和权限校验
 * 当前实现中，管理接口没有认证保护，生产环境需要：
 * 1. 在网关层添加 JWT Token 验证
 * 2. 添加角色权限校验（如：只有 admin 角色才能操作）
 * 3. 或使用 @PreAuthorize 注解进行方法级权限控制
 */
@RestController
@RequestMapping("/api/product/spu")
@RequiredArgsConstructor
@Tag(name = "商品管理", description = "商品的增删改查和上下架")
public class SpuController {

    private final SpuService spuService;

    /**
     * 商品列表分页查询
     *
     * @Valid 触发 DTO 上的校验注解（@Min、@Max 等）
     * 如果校验失败，会抛出 MethodArgumentNotValidException，被全局异常处理器捕获
     */
    @GetMapping("/list")
    @Operation(summary = "商品列表", description = "分页查询商品列表，支持按分类、关键词、状态筛选")
    public Result<PageResult<SpuVO>> listSpu(@Valid SpuQueryDTO query) {
        PageResult<SpuVO> result = spuService.listSpu(query);
        return Result.ok(result);
    }

    /**
     * 获取商品详情（含所有 SKU）
     */
    @GetMapping("/{id}")
    @Operation(summary = "商品详情", description = "获取商品详细信息，包含所有 SKU")
    public Result<SpuDetailVO> getSpuDetail(@PathVariable Long id) {
        SpuDetailVO detail = spuService.getSpuDetail(id);
        return Result.ok(detail);
    }

    /**
     * 添加商品（管理员）
     *
     * @RequestBody 接收 JSON 格式的请求体
     * @Valid 触发参数校验
     */
    @PostMapping
    @Operation(summary = "添加商品", description = "添加新的商品（管理员）")
    public Result<Long> addSpu(@Valid @RequestBody SpuSaveDTO dto) {
        Long spuId = spuService.addSpu(dto);
        return Result.ok(spuId);
    }

    /**
     * 修改商品（管理员）
     */
    @PutMapping("/{id}")
    @Operation(summary = "修改商品", description = "修改商品信息（管理员）")
    public Result<Void> updateSpu(@PathVariable Long id, @Valid @RequestBody SpuSaveDTO dto) {
        dto.setId(id);
        spuService.updateSpu(dto);
        return Result.ok();
    }

    /**
     * 上架商品（管理员）
     */
    @PutMapping("/{id}/on-sale")
    @Operation(summary = "上架商品", description = "将商品状态改为上架（管理员）")
    public Result<Void> onSale(@PathVariable Long id) {
        spuService.onSale(id);
        return Result.ok();
    }

    /**
     * 下架商品（管理员）
     */
    @PutMapping("/{id}/off-sale")
    @Operation(summary = "下架商品", description = "将商品状态改为下架（管理员）")
    public Result<Void> offSale(@PathVariable Long id) {
        spuService.offSale(id);
        return Result.ok();
    }
}
