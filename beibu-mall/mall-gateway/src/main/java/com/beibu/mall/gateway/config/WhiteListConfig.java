package com.beibu.mall.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 白名单配置：定义哪些接口不需要登录就能访问
 *
 * 为什么需要白名单？
 * 有些接口本身就不用登录就能用，比如：
 * - 登录接口（/api/user/login）：用户还没登录，怎么验证？
 * - 注册接口（/api/user/register）：新用户还没账号，怎么登录？
 * - 商品列表（/api/product/list）：游客也能看商品
 * - 验证码接口（/api/user/captcha）：登录前需要获取验证码
 *
 * 这些接口如果也要求 JWT，就会出现"死锁"：
 * 要登录 → 需要 JWT → 要获取 JWT → 需要登录 → ...
 *
 * 所以白名单里的接口，直接放行，不需要验证 JWT。
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.white-list")
public class WhiteListConfig {

    /**
     * 白名单 URL 列表
     * 支持 Ant 风格的路径匹配，比如：
     * - /api/user/login：精确匹配
     * - /api/user/register/**：匹配 register 下所有子路径
     * - /api/product/list*：匹配 list 开头的路径
     */
    private List<String> urls = new ArrayList<>();
}
