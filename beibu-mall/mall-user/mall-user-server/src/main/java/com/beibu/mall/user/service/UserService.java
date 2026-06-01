package com.beibu.mall.user.service;

import com.beibu.mall.user.api.dto.LoginDTO;
import com.beibu.mall.user.api.dto.LoginVO;
import com.beibu.mall.user.api.dto.RegisterDTO;
import com.beibu.mall.user.api.dto.UserVO;

/**
 * 用户服务接口
 *
 * 接口的作用：定义"能做什么"，不关心"怎么做"。
 * 好处：
 * 1. 解耦：Controller 只依赖接口，不关心实现类是谁
 * 2. 可测试：单元测试时可以用 Mock 实现类
 * 3. 可替换：以后换实现（比如加缓存）不用改 Controller
 */
public interface UserService {

    /**
     * 用户注册
     * @param dto 注册参数
     */
    void register(RegisterDTO dto);

    /**
     * 用户登录
     * @param dto 登录参数
     * @return 登录结果（包含 JWT Token）
     */
    LoginVO login(LoginDTO dto);

    /**
     * 根据用户ID查询用户信息
     * @param userId 用户ID
     * @return 用户信息（不含密码）
     */
    UserVO getUserById(Long userId);
}
