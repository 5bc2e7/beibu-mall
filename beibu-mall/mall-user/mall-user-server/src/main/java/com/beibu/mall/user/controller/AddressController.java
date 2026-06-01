package com.beibu.mall.user.controller;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.user.api.dto.AddressDTO;
import com.beibu.mall.user.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
@Tag(name = "收货地址管理", description = "收货地址的增删改查")
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    @Operation(summary = "添加收货地址")
    public Result<Void> addAddress(HttpServletRequest request, @Valid @RequestBody AddressDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        addressService.addAddress(userId, dto);
        return Result.ok();
    }

    @PutMapping
    @Operation(summary = "修改收货地址")
    public Result<Void> updateAddress(HttpServletRequest request, @Valid @RequestBody AddressDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        addressService.updateAddress(userId, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除收货地址")
    public Result<Void> deleteAddress(HttpServletRequest request, @PathVariable("id") Long id) {
        Long userId = (Long) request.getAttribute("userId");
        addressService.deleteAddress(userId, id);
        return Result.ok();
    }

    @GetMapping
    @Operation(summary = "查询用户所有收货地址")
    public Result<List<AddressDTO>> listAddresses(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<AddressDTO> list = addressService.listAddresses(userId);
        return Result.ok(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询地址详情")
    public Result<AddressDTO> getAddress(HttpServletRequest request, @PathVariable("id") Long id) {
        Long userId = (Long) request.getAttribute("userId");
        AddressDTO dto = addressService.getAddress(userId, id);
        return Result.ok(dto);
    }
}
