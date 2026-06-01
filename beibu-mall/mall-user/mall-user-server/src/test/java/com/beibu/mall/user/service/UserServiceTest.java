package com.beibu.mall.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.user.api.dto.LoginDTO;
import com.beibu.mall.user.api.dto.LoginVO;
import com.beibu.mall.user.api.dto.RegisterDTO;
import com.beibu.mall.user.entity.User;
import com.beibu.mall.user.mapper.UserMapper;
import com.beibu.mall.user.service.impl.UserServiceImpl;
import com.beibu.mall.user.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.DigestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试
 *
 * 什么是单元测试？
 * - 测试一个类的方法是否正确，不依赖数据库、网络等外部资源
 * - 用 Mock（模拟）代替真实的 Mapper，只测 Service 逻辑
 *
 * 什么是 Mockito？
 * - 一个 Mock 框架，可以模拟任何对象的行为
 * - 比如：模拟 UserMapper.selectCount() 返回指定值，而不是真的查数据库
 *
 * @ExtendWith(MockitoExtension.class)：启用 Mockito 注解
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock  // 创建一个 Mock 对象
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks  // 把 Mock 对象注入到 UserServiceImpl 中
    private UserServiceImpl userService;

    private RegisterDTO registerDTO;
    private LoginDTO loginDTO;
    private User testUser;

    @BeforeEach  // 每个测试方法执行前都会调用
    void setUp() {
        // 准备测试数据
        registerDTO = new RegisterDTO();
        registerDTO.setUsername("testuser");
        registerDTO.setPassword("123456");
        registerDTO.setPhone("13800138000");

        loginDTO = new LoginDTO();
        loginDTO.setUsername("testuser");
        loginDTO.setPassword("123456");

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword(DigestUtils.md5DigestAsHex("123456".getBytes()));
        testUser.setPhone("13800138000");
        testUser.setNickname("testuser");
        testUser.setStatus(1);
    }

    @Test
    @DisplayName("注册成功 - 用户名和手机号都不存在")
    void register_success() {
        // given：准备条件
        // 模拟数据库中没有同名用户
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        // 模拟插入成功
        when(userMapper.insert(any(User.class))).thenReturn(1);

        // when：执行被测试的方法
        assertDoesNotThrow(() -> userService.register(registerDTO));

        // then：验证结果
        // 验证 insert 方法被调用了 1 次
        verify(userMapper, times(1)).insert(any(User.class));
    }

    @Test
    @DisplayName("注册失败 - 用户名已存在")
    void register_usernameExists() {
        // given：模拟数据库中已有同名用户
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        // when & then：期望抛出 BizException
        BizException exception = assertThrows(BizException.class,
                () -> userService.register(registerDTO));

        // 验证异常信息
        assertEquals(40001, exception.getCode());
        assertEquals("用户名已存在", exception.getMessage());

        // 验证 insert 没有被调用（因为注册失败了）
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("登录成功")
    void login_success() {
        // given
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);
        when(jwtUtil.generateToken(1L, "testuser")).thenReturn("mock-jwt-token");

        // when
        LoginVO result = userService.login(loginDTO);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        assertEquals("testuser", result.getUsername());
        assertEquals("mock-jwt-token", result.getToken());
    }

    @Test
    @DisplayName("登录失败 - 密码错误")
    void login_wrongPassword() {
        // given
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);
        loginDTO.setPassword("wrong_password");

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> userService.login(loginDTO));

        assertEquals(40003, exception.getCode());
        assertEquals("用户名或密码错误", exception.getMessage());
    }

    @Test
    @DisplayName("登录失败 - 用户不存在")
    void login_userNotFound() {
        // given：模拟数据库中没有该用户
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> userService.login(loginDTO));

        assertEquals(40003, exception.getCode());
    }

    @Test
    @DisplayName("登录失败 - 账号被禁用")
    void login_accountDisabled() {
        // given
        testUser.setStatus(0); // 禁用状态
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);

        // when & then
        BizException exception = assertThrows(BizException.class,
                () -> userService.login(loginDTO));

        assertEquals(40004, exception.getCode());
        assertEquals("账号已被禁用", exception.getMessage());
    }
}
