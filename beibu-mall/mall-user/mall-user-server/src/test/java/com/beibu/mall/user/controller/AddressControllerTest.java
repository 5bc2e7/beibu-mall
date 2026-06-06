package com.beibu.mall.user.controller;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.user.api.dto.AddressDTO;
import com.beibu.mall.user.service.AddressService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressControllerTest {

    @Mock
    private AddressService addressService;

    @InjectMocks
    private AddressController addressController;

    @Test
    @DisplayName("添加收货地址成功")
    void addAddress_success() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("userId")).thenReturn(1L);

        AddressDTO dto = new AddressDTO();
        dto.setReceiverName("张三");
        dto.setReceiverPhone("13800138000");
        dto.setProvince("广西");
        dto.setCity("南宁");
        dto.setDistrict("青秀区");
        dto.setDetail("民族大道100号");

        doNothing().when(addressService).addAddress(1L, dto);

        Result<Void> result = addressController.addAddress(request, dto);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(addressService, times(1)).addAddress(1L, dto);
    }

    @Test
    @DisplayName("查询用户所有收货地址成功")
    void listAddresses_success() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("userId")).thenReturn(1L);

        AddressDTO addr1 = new AddressDTO();
        addr1.setId(1L);
        addr1.setReceiverName("张三");
        addr1.setReceiverPhone("13800138000");
        addr1.setProvince("广西");
        addr1.setCity("南宁");
        addr1.setDistrict("青秀区");
        addr1.setDetail("民族大道100号");

        AddressDTO addr2 = new AddressDTO();
        addr2.setId(2L);
        addr2.setReceiverName("李四");
        addr2.setReceiverPhone("13900139000");
        addr2.setProvince("广西");
        addr2.setCity("柳州");
        addr2.setDistrict("城中区");
        addr2.setDetail("解放路50号");

        List<AddressDTO> addresses = Arrays.asList(addr1, addr2);
        when(addressService.listAddresses(1L)).thenReturn(addresses);

        Result<List<AddressDTO>> result = addressController.listAddresses(request);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals(2, result.getData().size());
        assertEquals("张三", result.getData().get(0).getReceiverName());
        assertEquals("李四", result.getData().get(1).getReceiverName());
        verify(addressService, times(1)).listAddresses(1L);
    }

    @Test
    @DisplayName("删除收货地址成功")
    void deleteAddress_success() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("userId")).thenReturn(1L);

        doNothing().when(addressService).deleteAddress(1L, 10L);

        Result<Void> result = addressController.deleteAddress(request, 10L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(addressService, times(1)).deleteAddress(1L, 10L);
    }
}
