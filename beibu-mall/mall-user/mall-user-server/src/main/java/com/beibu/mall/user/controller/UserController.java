package com.beibu.mall.user.controller;

import com.beibu.mall.common.result.Result;
import com.beibu.mall.user.api.dto.*;
import com.beibu.mall.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 *
 * @RestController = @Controller + @ResponseBody
 * - @Controller：标记这是一个控制器类
 * - @ResponseBody：方法返回值自动转成 JSON
 *
 * @RequestMapping("/api/user")：统一 URL 前缀
 * 所有接口都以 /api/user 开头，如 /api/user/register
 *
 * @Tag 是 Knife4j/Swagger 的注解，用于 API 文档分组
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户注册、登录、信息查询")
public class UserController {

    private final UserService userService;

    /**
     * 用户注册
     *
     * @Valid 触发 DTO 上的校验注解（@NotBlank、@Pattern 等）
     * 如果校验失败，会抛出 MethodArgumentNotValidException，被全局异常处理器捕获
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "用户名+密码+手机号注册")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.ok();
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户名+密码登录，返回JWT Token")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        LoginVO vo = userService.login(dto);
        return Result.ok(vo);
    }

    /**
     * 查询当前登录用户信息
     *
     * @RequestAttribute 从 request 属性中取值（由 JwtInterceptor 设置）
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "需要登录（携带JWT Token）")
    public Result<UserVO> getCurrentUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        UserVO vo = userService.getUserById(userId);
        return Result.ok(vo);
    }
}
