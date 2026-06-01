package com.beibu.mall.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.user.api.dto.AddressDTO;
import com.beibu.mall.user.entity.Address;
import com.beibu.mall.user.mapper.AddressMapper;
import com.beibu.mall.user.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressMapper addressMapper;

    @Override
    @Transactional  // 事务：如果设为默认地址失败，整体会回滚
    public void addAddress(Long userId, AddressDTO dto) {
        // 如果设为默认地址，先把其他地址的默认状态取消
        if (dto.getIsDefault() != null && dto.getIsDefault() == 1) {
            clearDefaultAddress(userId);
        }

        Address address = new Address();
        BeanUtils.copyProperties(dto, address); // 把 DTO 的属性复制到实体
        address.setUserId(userId);
        addressMapper.insert(address);
    }

    @Override
    @Transactional
    public void updateAddress(Long userId, AddressDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(40010, "地址ID不能为空");
        }

        // 验证地址是否属于当前用户（防止修改别人的地址）
        Address existing = addressMapper.selectById(dto.getId());
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BizException(40011, "地址不存在");
        }

        // 如果设为默认地址，先取消其他默认
        if (dto.getIsDefault() != null && dto.getIsDefault() == 1) {
            clearDefaultAddress(userId);
        }

        Address address = new Address();
        BeanUtils.copyProperties(dto, address);
        address.setUserId(userId);
        addressMapper.updateById(address);
    }

    @Override
    public void deleteAddress(Long userId, Long addressId) {
        Address existing = addressMapper.selectById(addressId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BizException(40011, "地址不存在");
        }
        addressMapper.deleteById(addressId);
    }

    @Override
    public List<AddressDTO> listAddresses(Long userId) {
        List<Address> addresses = addressMapper.selectList(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .orderByDesc(Address::getIsDefault) // 默认地址排前面
                        .orderByDesc(Address::getUpdateTime)
        );

        return addresses.stream().map(addr -> {
            AddressDTO dto = new AddressDTO();
            BeanUtils.copyProperties(addr, dto);
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public AddressDTO getAddress(Long userId, Long addressId) {
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BizException(40011, "地址不存在");
        }

        AddressDTO dto = new AddressDTO();
        BeanUtils.copyProperties(address, dto);
        return dto;
    }

    /**
     * 清除用户的默认地址标记
     */
    private void clearDefaultAddress(Long userId) {
        Address update = new Address();
        update.setIsDefault(0);
        addressMapper.update(update,
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .eq(Address::getIsDefault, 1)
        );
    }
}
