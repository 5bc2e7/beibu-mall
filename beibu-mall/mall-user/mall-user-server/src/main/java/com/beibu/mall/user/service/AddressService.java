package com.beibu.mall.user.service;

import com.beibu.mall.user.api.dto.AddressDTO;

import java.util.List;

public interface AddressService {

    /**
     * 添加收货地址
     */
    void addAddress(Long userId, AddressDTO dto);

    /**
     * 修改收货地址
     */
    void updateAddress(Long userId, AddressDTO dto);

    /**
     * 删除收货地址
     */
    void deleteAddress(Long userId, Long addressId);

    /**
     * 查询用户的所有收货地址
     */
    List<AddressDTO> listAddresses(Long userId);

    /**
     * 查询地址详情
     */
    AddressDTO getAddress(Long userId, Long addressId);
}
