package com.beibu.mall.common.config;

import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.common.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * 作用：统一捕获 Controller 层抛出的异常，转换成 Result 格式返回给前端。
 *
 * 为什么需要它？
 * 如果没有这个类，异常会返回默认的 Spring 错误页面（Whitelabel Error Page），
 * 格式不统一，前端无法解析。有了它，所有异常都会变成：
 * { "code": xxx, "msg": "错误信息", "data": null }
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 * - @ControllerAdvice：拦截所有 Controller 的异常
 * - @ResponseBody：返回值自动转成 JSON
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常（我们自己抛出的）
     * 比如：用户名已存在、余额不足等
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        // 业务异常不需要打印堆栈，只记 warn 级别日志
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（@Valid 注解触发）
     * 比如：@NotBlank 校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        // 拼接所有字段的错误信息
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.fail(400, msg);
    }

    /**
     * 处理单个参数校验异常（@Validated 用在方法参数上时触发）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.fail(400, msg);
    }

    /**
     * 兜底处理：所有未被捕获的异常
     * 这是最后的安全网，防止异常信息泄露给前端
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        // 未知异常需要打印完整堆栈，方便排查
        log.error("系统异常: ", e);
        return Result.fail(500, "系统繁忙，请稍后重试");
    }
}
