package com.beibu.mall.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.user.api.dto.AddressDTO;
import com.beibu.mall.user.entity.Address;
import com.beibu.mall.user.mapper.AddressMapper;
import com.beibu.mall.user.service.impl.AddressServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 地址服务测试
 *
 * 测试 AddressServiceImpl 的 CRUD 操作，包括：
 * - 添加地址
 * - 修改地址
 * - 删除地址
 * - 查询地址列表
 * - 查询单个地址
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("地址服务测试")
class AddressServiceTest {

    @Mock
    private AddressMapper addressMapper;

    @InjectMocks
    private AddressServiceImpl addressService;

    private Long userId;
    private Address testAddress;
    private AddressDTO testAddressDTO;

    @BeforeEach
    void setUp() {
        userId = 1001L;

        // 测试地址实体
        testAddress = new Address();
        testAddress.setId(1L);
        testAddress.setUserId(userId);
        testAddress.setReceiverName("张三");
        testAddress.setReceiverPhone("13800138000");
        testAddress.setProvince("广西");
        testAddress.setCity("北海");
        testAddress.setDistrict("海城区");
        testAddress.setDetail("北部湾大道1号");
        testAddress.setIsDefault(1);

        // 测试地址 DTO
        testAddressDTO = new AddressDTO();
        testAddressDTO.setReceiverName("张三");
        testAddressDTO.setReceiverPhone("13800138000");
        testAddressDTO.setProvince("广西");
        testAddressDTO.setCity("北海");
        testAddressDTO.setDistrict("海城区");
        testAddressDTO.setDetail("北部湾大道1号");
        testAddressDTO.setIsDefault(1);
    }

    // ==================== addAddress 测试 ====================

    @Test
    @DisplayName("添加地址 - 成功")
    void addAddress_success() {
        // given
        when(addressMapper.insert(any(Address.class))).thenReturn(1);

        // when
        assertDoesNotThrow(() -> addressService.addAddress(userId, testAddressDTO));

        // then
        verify(addressMapper, times(1)).insert(any(Address.class));
    }

    @Test
    @DisplayName("添加地址 - 设为默认地址时清除其他默认")
    void addAddress_defaultAddress_clearsOtherDefaults() {
        // given
        testAddressDTO.setIsDefault(1);
        when(addressMapper.update(any(Address.class), any(LambdaQueryWrapper.class))).thenReturn(1);
        when(addressMapper.insert(any(Address.class))).thenReturn(1);

        // when
        assertDoesNotThrow(() -> addressService.addAddress(userId, testAddressDTO));

        // then
        verify(addressMapper, times(1)).update(any(Address.class), any(LambdaQueryWrapper.class));
        verify(addressMapper, times(1)).insert(any(Address.class));
    }

    @Test
    @DisplayName("添加地址 - 非默认地址不清除其他默认")
    void addAddress_nonDefaultAddress_noClear() {
        // given
        testAddressDTO.setIsDefault(0);
        when(addressMapper.insert(any(Address.class))).thenReturn(1);

        // when
        assertDoesNotThrow(() -> addressService.addAddress(userId, testAddressDTO));

        // then
        verify(addressMapper, never()).update(any(Address.class), any(LambdaQueryWrapper.class));
        verify(addressMapper, times(1)).insert(any(Address.class));
    }

    // ==================== updateAddress 测试 ====================

    @Test
    @DisplayName("修改地址 - 成功")
    void updateAddress_success() {
        // given
        testAddressDTO.setId(1L);
        when(addressMapper.selectById(1L)).thenReturn(testAddress);
        when(addressMapper.updateById(any(Address.class))).thenReturn(1);

        // when
        assertDoesNotThrow(() -> addressService.updateAddress(userId, testAddressDTO));

        // then
        verify(addressMapper, times(1)).selectById(1L);
        verify(addressMapper, times(1)).updateById(any(Address.class));
    }

    @Test
    @DisplayName("修改地址 - ID为空抛出异常")
    void updateAddress_nullId_throwsException() {
        // given
        testAddressDTO.setId(null);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> addressService.updateAddress(userId, testAddressDTO));

        assertEquals(40010, exception.getCode());
        assertTrue(exception.getMessage().contains("地址ID不能为空"));

        verify(addressMapper, never()).selectById(any());
        verify(addressMapper, never()).updateById(any(Address.class));
    }

    @Test
    @DisplayName("修改地址 - 地址不存在抛出异常")
    void updateAddress_notFound_throwsException() {
        // given
        testAddressDTO.setId(999L);
        when(addressMapper.selectById(999L)).thenReturn(null);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> addressService.updateAddress(userId, testAddressDTO));

        assertEquals(40011, exception.getCode());
        assertTrue(exception.getMessage().contains("地址不存在"));

        verify(addressMapper, never()).updateById(any(Address.class));
    }

    @Test
    @DisplayName("修改地址 - 地址不属于当前用户抛出异常")
    void updateAddress_notOwner_throwsException() {
        // given
        testAddressDTO.setId(1L);
        Address otherUserAddress = new Address();
        otherUserAddress.setId(1L);
        otherUserAddress.setUserId(9999L); // 其他用户
        when(addressMapper.selectById(1L)).thenReturn(otherUserAddress);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> addressService.updateAddress(userId, testAddressDTO));

        assertEquals(40011, exception.getCode());
        assertTrue(exception.getMessage().contains("地址不存在"));

        verify(addressMapper, never()).updateById(any(Address.class));
    }

    @Test
    @DisplayName("修改地址 - 设为默认地址时清除其他默认")
    void updateAddress_defaultAddress_clearsOtherDefaults() {
        // given
        testAddressDTO.setId(1L);
        testAddressDTO.setIsDefault(1);
        when(addressMapper.selectById(1L)).thenReturn(testAddress);
        when(addressMapper.update(any(Address.class), any(LambdaQueryWrapper.class))).thenReturn(1);
        when(addressMapper.updateById(any(Address.class))).thenReturn(1);

        // when
        assertDoesNotThrow(() -> addressService.updateAddress(userId, testAddressDTO));

        // then
        verify(addressMapper, times(1)).update(any(Address.class), any(LambdaQueryWrapper.class));
        verify(addressMapper, times(1)).updateById(any(Address.class));
    }

    // ==================== deleteAddress 测试 ====================

    @Test
    @DisplayName("删除地址 - 成功")
    void deleteAddress_success() {
        // given
        when(addressMapper.selectById(1L)).thenReturn(testAddress);
        when(addressMapper.deleteById(1L)).thenReturn(1);

        // when
        assertDoesNotThrow(() -> addressService.deleteAddress(userId, 1L));

        // then
        verify(addressMapper, times(1)).selectById(1L);
        verify(addressMapper, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("删除地址 - 地址不存在抛出异常")
    void deleteAddress_notFound_throwsException() {
        // given
        when(addressMapper.selectById(999L)).thenReturn(null);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> addressService.deleteAddress(userId, 999L));

        assertEquals(40011, exception.getCode());
        assertTrue(exception.getMessage().contains("地址不存在"));

        verify(addressMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除地址 - 地址不属于当前用户抛出异常")
    void deleteAddress_notOwner_throwsException() {
        // given
        Address otherUserAddress = new Address();
        otherUserAddress.setId(1L);
        otherUserAddress.setUserId(9999L);
        when(addressMapper.selectById(1L)).thenReturn(otherUserAddress);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> addressService.deleteAddress(userId, 1L));

        assertEquals(40011, exception.getCode());
        assertTrue(exception.getMessage().contains("地址不存在"));

        verify(addressMapper, never()).deleteById(anyLong());
    }

    // ==================== listAddresses 测试 ====================

    @Test
    @DisplayName("查询地址列表 - 成功返回列表")
    void listAddresses_success() {
        // given
        Address address2 = new Address();
        address2.setId(2L);
        address2.setUserId(userId);
        address2.setReceiverName("李四");
        address2.setIsDefault(0);

        when(addressMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(testAddress, address2));

        // when
        List<AddressDTO> result = addressService.listAddresses(userId);

        // then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("张三", result.get(0).getReceiverName());
        assertEquals("李四", result.get(1).getReceiverName());

        verify(addressMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("查询地址列表 - 无地址返回空列表")
    void listAddresses_emptyList() {
        // given
        when(addressMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        // when
        List<AddressDTO> result = addressService.listAddresses(userId);

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(addressMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    // ==================== getAddress 测试 ====================

    @Test
    @DisplayName("查询单个地址 - 成功")
    void getAddress_success() {
        // given
        when(addressMapper.selectById(1L)).thenReturn(testAddress);

        // when
        AddressDTO result = addressService.getAddress(userId, 1L);

        // then
        assertNotNull(result);
        assertEquals("张三", result.getReceiverName());
        assertEquals("13800138000", result.getReceiverPhone());
        assertEquals("广西", result.getProvince());
        assertEquals("北海", result.getCity());

        verify(addressMapper, times(1)).selectById(1L);
    }

    @Test
    @DisplayName("查询单个地址 - 地址不存在抛出异常")
    void getAddress_notFound_throwsException() {
        // given
        when(addressMapper.selectById(999L)).thenReturn(null);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> addressService.getAddress(userId, 999L));

        assertEquals(40011, exception.getCode());
        assertTrue(exception.getMessage().contains("地址不存在"));
    }

    @Test
    @DisplayName("查询单个地址 - 地址不属于当前用户抛出异常")
    void getAddress_notOwner_throwsException() {
        // given
        Address otherUserAddress = new Address();
        otherUserAddress.setId(1L);
        otherUserAddress.setUserId(9999L);
        when(addressMapper.selectById(1L)).thenReturn(otherUserAddress);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> addressService.getAddress(userId, 1L));

        assertEquals(40011, exception.getCode());
        assertTrue(exception.getMessage().contains("地址不存在"));
    }
}
