# Mall Gateway 测试说明

## 网关功能概述

网关（Gateway）就像商场的"门卫"，所有请求都要经过它。它主要做三件事：

1. **路由转发**：根据请求路径，把请求送到对应的微服务
2. **登录校验**：检查请求是否带了有效的 JWT 令牌
3. **跨域处理**：解决浏览器的"同源策略"限制

## 前置条件

1. 启动 Nacos 服务（默认端口 8848）
2. 启动 mall-gateway 服务（端口 9000）
3. 启动 mall-user 服务（或其他业务服务）

## 测试场景

### 场景1：白名单接口（不需要登录）

这些接口不需要 JWT 令牌，直接访问就能成功。

```bash
# 测试用户登录接口（白名单）
curl -X POST http://localhost:9000/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test", "password":"123456"}'

# 预期结果：请求成功转发到用户服务，返回登录结果
# 注意：即使没有 JWT 令牌，也不会被拦截
```

```bash
# 测试商品列表接口（白名单）
curl http://localhost:9000/api/product/list

# 预期结果：请求成功转发到商品服务，返回商品列表
```

### 场景2：需要登录的接口（不带令牌）

这些接口需要 JWT 令牌，如果不带令牌会被拦截。

```bash
# 测试获取用户信息接口（需要登录）
curl http://localhost:9000/api/user/info

# 预期结果：
# HTTP 状态码：401 Unauthorized
# 响应体：
# {
#   "code": 401,
#   "msg": "未登录，请先登录",
#   "data": null
# }
```

### 场景3：需要登录的接口（带无效令牌）

如果令牌无效（过期、格式错误、签名不对），也会被拦截。

```bash
# 测试带无效令牌
curl http://localhost:9000/api/user/info \
  -H "Authorization: Bearer invalid_token_here"

# 预期结果：
# HTTP 状态码：401 Unauthorized
# 响应体：
# {
#   "code": 401,
#   "msg": "登录已过期，请重新登录",
#   "data": null
# }
```

### 场景4：需要登录的接口（带有效令牌）

如果令牌有效，请求会成功转发到下游服务。

```bash
# 测试带有效令牌
# 注意：这里的 token 需要先通过登录接口获取
curl http://localhost:9000/api/user/info \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xxxxx"

# 预期结果：
# HTTP 状态码：200 OK
# 响应体：用户服务返回的用户信息
```

### 场景5：跨域请求

浏览器会先发送 OPTIONS 预检请求，然后才发送真正的请求。

```bash
# 测试预检请求
curl -X OPTIONS http://localhost:9000/api/user/login \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type"

# 预期结果：
# HTTP 状态码：200 OK
# 响应头包含：
# Access-Control-Allow-Origin: http://localhost:3000
# Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS
# Access-Control-Allow-Headers: Content-Type,Authorization
```

## 测试工具推荐

### 1. curl（命令行）
适合快速测试，上面的例子都是用 curl。

### 2. Postman（图形化界面）
适合复杂测试，可以保存请求历史。

### 3. 浏览器开发者工具
- F12 打开开发者工具
- Network 标签页查看请求和响应
- 可以看到预检请求（OPTIONS）和真正的请求

### 4. 前端代码测试
```javascript
// 测试白名单接口（不需要 token）
fetch('http://localhost:9000/api/product/list')
  .then(res => res.json())
  .then(data => console.log('商品列表:', data))

// 测试需要登录的接口（带 token）
fetch('http://localhost:9000/api/user/info', {
  headers: {
    'Authorization': 'Bearer ' + localStorage.getItem('token')
  }
})
  .then(res => res.json())
  .then(data => console.log('用户信息:', data))
```

## 常见问题

### Q1：为什么白名单接口不需要登录？
A：有些接口本身就不用登录就能用，比如登录接口。如果登录接口也要求 JWT，就会出现"死锁"：要登录 → 需要 JWT → 要获取 JWT → 需要登录 → ...

### Q2：JWT 令牌从哪里来？
A：用户登录成功后，用户服务会签发一个 JWT 令牌返回给前端。前端把这个令牌保存起来（通常存在 localStorage），以后每次请求都带上它。

### Q3：为什么网关要验证 JWT，而不是每个服务自己验证？
A：统一在网关验证的好处：
1. 方便：不用每个服务都写一遍验证逻辑
2. 安全：没有令牌的请求根本到不了业务服务
3. 高效：网关是第一个处理请求的地方，尽早拦截无效请求

### Q4：下游服务怎么获取用户信息？
A：网关验证令牌后，会把用户信息（用户ID、用户名）添加到请求头里传给下游服务。下游服务直接从请求头里取就行，不需要再解析 JWT。

## 调试技巧

### 1. 查看网关日志
在 application.yml 中添加：
```yaml
logging:
  level:
    com.beibu.mall.gateway: DEBUG
```

### 2. 查看路由信息
访问：http://localhost:9000/actuator/gateway/routes
（需要添加 actuator 依赖）

### 3. 测试路由是否正确
```bash
# 测试用户服务路由
curl http://localhost:9000/api/user/test

# 测试商品服务路由
curl http://localhost:9000/api/product/test
```

## 下一步

1. 实现用户服务的登录接口，返回真正的 JWT 令牌
2. 测试完整的登录流程
3. 添加更多业务服务的路由
