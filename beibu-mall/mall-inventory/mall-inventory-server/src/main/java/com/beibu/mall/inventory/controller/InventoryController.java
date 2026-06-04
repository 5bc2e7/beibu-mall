package com.beibu.mall.inventory.controller;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.inventory.api.dto.StockOperationDTO;
import com.beibu.mall.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/occupy")
    public Result<Void> occupyStock(@Valid @RequestBody StockOperationDTO stockOperationDTO) {
        log.info("预占库存请求：skuId={}, quantity={}, orderId={}",
                stockOperationDTO.getSkuId(), stockOperationDTO.getQuantity(), stockOperationDTO.getOrderId());

        inventoryService.occupyStock(
                stockOperationDTO.getSkuId(),
                stockOperationDTO.getQuantity(),
                stockOperationDTO.getOrderId()
        );

        return Result.ok();
    }

    @PostMapping("/release")
    public Result<Void> releaseStock(@Valid @RequestBody StockOperationDTO stockOperationDTO) {
        log.info("释放库存请求：skuId={}, quantity={}, orderId={}",
                stockOperationDTO.getSkuId(), stockOperationDTO.getQuantity(), stockOperationDTO.getOrderId());

        inventoryService.releaseStock(
                stockOperationDTO.getSkuId(),
                stockOperationDTO.getQuantity(),
                stockOperationDTO.getOrderId()
        );

        return Result.ok();
    }

    @PostMapping("/confirm")
    public Result<Void> confirmDeduct(@Valid @RequestBody StockOperationDTO stockOperationDTO) {
        log.info("确认扣减请求：skuId={}, quantity={}, orderId={}",
                stockOperationDTO.getSkuId(), stockOperationDTO.getQuantity(), stockOperationDTO.getOrderId());

        inventoryService.confirmDeduct(
                stockOperationDTO.getSkuId(),
                stockOperationDTO.getQuantity(),
                stockOperationDTO.getOrderId()
        );

        return Result.ok();
    }
}
