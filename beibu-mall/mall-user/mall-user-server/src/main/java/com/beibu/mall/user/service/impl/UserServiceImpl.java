package com.beibu.mall.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.user.api.dto.*;
import com.beibu.mall.user.entity.User;
import com.beibu.mall.user.mapper.UserMapper;
import com.beibu.mall.user.service.UserService;
import com.beibu.mall.user.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

/**
 * 用户服务实现类
 *
 * @Service 标记这是一个 Service 类，Spring 会自动创建它的实例（Bean）
 * @RequiredArgsConstructor 是 Lombok 注解，自动生成包含 final 字段的构造方法
 * Spring 会通过构造方法自动注入 UserMapper 等依赖（推荐方式）
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    /**
     * 用户注册
     */
    @Override
    public void register(RegisterDTO dto) {
        // 1. 检查用户名是否已存在
        // LambdaQueryWrapper 是 MyBatis-Plus 的查询条件构造器
        // 相当于 SQL: SELECT COUNT(*) FROM user WHERE username = ? AND deleted = 0
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername())
        );
        if (count > 0) {
            throw new BizException(40001, "用户名已存在");
        }

        // 2. 检查手机号是否已被注册
        count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone())
        );
        if (count > 0) {
            throw new BizException(40002, "手机号已被注册");
        }

        // 3. 构建用户实体
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPhone(dto.getPhone());
        user.setNickname(dto.getUsername()); // 默认用用户名作为昵称
        user.setStatus(1); // 默认正常状态

        // 4. 密码加密
        // 为什么不存明文？如果数据库被黑客攻破，明文密码会导致所有用户的密码泄露
        // BCrypt 的特点：
        // - 每次加密同一个密码，得到的结果不同（因为有随机盐）
        // - 验证时用 BCrypt.checkpw() 对比，不需要解密
        // - 计算慢（故意的），暴力破解成本极高
        //
        // 我们这里用 MD5 模拟简单加密（教学用，生产环境请用 BCrypt）
        // Spring 自带的 DigestUtils.md5DigestAsHex() 可以计算 MD5
        String encryptedPassword = DigestUtils.md5DigestAsHex(dto.getPassword().getBytes());
        user.setPassword(encryptedPassword);

        // 5. 插入数据库
        userMapper.insert(user);
    }

    /**
     * 用户登录
     */
    @Override
    public LoginVO login(LoginDTO dto) {
        // 1. 根据用户名查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername())
        );
        if (user == null) {
            throw new BizException(40003, "用户名或密码错误");
        }

        // 2. 验证密码
        // 将前端传来的明文密码用同样的方式加密，然后对比数据库中的密文
        String encryptedPassword = DigestUtils.md5DigestAsHex(dto.getPassword().getBytes());
        if (!encryptedPassword.equals(user.getPassword())) {
            throw new BizException(40003, "用户名或密码错误");
        }

        // 3. 检查账号状态
        if (user.getStatus() == 0) {
            throw new BizException(40004, "账号已被禁用");
        }

        // 4. 生成 JWT Token
        // JWT 是什么？
        // JWT = JSON Web Token，一种轻量级的身份认证方案
        // 结构：Header.Payload.Signature（用 . 分隔的三段 Base64）
        // - Header: 声明算法（如 HS256）
        // - Payload: 存放用户信息（如 userId、username）
        // - Signature: 用密钥对前两段签名，防止篡改
        //
        // 为什么用 JWT 而不是 Session？
        // - Session 存在服务器内存，多台服务器需要共享 Session（麻烦）
        // - JWT 自带用户信息，服务器不需要存储，天然支持分布式
        //
        // 使用方式：
        // 1. 登录成功后返回 JWT 给前端
        // 2. 前端每次请求把 JWT 放在 Header 里：Authorization: Bearer <token>
        // 3. 后端拦截器验证 JWT 的签名和有效期
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // 5. 构建返回结果
        return LoginVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .token(token)
                .build();
    }

    /**
     * 根据ID查询用户信息
     */
    @Override
    public UserVO getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(40005, "用户不存在");
        }

        // 实体转 VO（手动转换，不把 password 等敏感字段暴露出去）
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setPhone(user.getPhone());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setGender(user.getGender());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
