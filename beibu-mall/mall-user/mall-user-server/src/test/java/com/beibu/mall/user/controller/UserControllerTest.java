package com.beibu.mall.user.controller;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.user.api.dto.*;
import com.beibu.mall.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @Test
    @DisplayName("用户注册成功")
    void register_success() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newuser");
        dto.setPassword("123456");
        dto.setPhone("13900139000");

        doNothing().when(userService).register(dto);

        Result<Void> result = userController.register(dto);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(userService, times(1)).register(dto);
    }

    @Test
    @DisplayName("用户登录成功")
    void login_success() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("testuser");
        dto.setPassword("123456");

        LoginVO loginVO = LoginVO.builder()
                .userId(1L)
                .username("testuser")
                .nickname("testuser")
                .token("jwt-token-123")
                .build();

        when(userService.login(dto)).thenReturn(loginVO);

        Result<LoginVO> result = userController.login(dto);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals(1L, result.getData().getUserId());
        assertEquals("testuser", result.getData().getUsername());
        assertEquals("jwt-token-123", result.getData().getToken());
        verify(userService, times(1)).login(dto);
    }

    @Test
    @DisplayName("获取当前用户信息成功")
    void getCurrentUser_success() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("userId")).thenReturn(1L);

        UserVO userVO = new UserVO();
        userVO.setId(1L);
        userVO.setUsername("testuser");
        userVO.setPhone("13800138000");
        userVO.setNickname("testuser");

        when(userService.getUserById(1L)).thenReturn(userVO);

        Result<UserVO> result = userController.getCurrentUser(request);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals(1L, result.getData().getId());
        assertEquals("testuser", result.getData().getUsername());
        assertEquals("13800138000", result.getData().getPhone());
        verify(userService, times(1)).getUserById(1L);
    }
}
