# 秒杀服务 JMeter 压测指南

## JMeter 压测方法

### 1. 安装 JMeter

```bash
# 下载 Apache JMeter
# https://jmeter.apache.org/download_jmeter.cgi
# 解压后运行 bin/jmeter (Linux/Mac) 或 bin/jmeter.bat (Windows)
```

### 2. 创建测试计划

1. **添加线程组**（Thread Group）
   - 线程数：200（模拟 200 个用户）
   - Ramp-Up 时间：10 秒（10 秒内启动 200 个线程）
   - 循环次数：1（每个用户只抢一次）

2. **添加 HTTP 请求**
   - 协议：http
   - 服务器：localhost
   - 端口：9008
   - 方法：POST
   - 路径：/api/seckill/do
   - Body（JSON）：
     ```json
     {
       "activityId": 1001,
       "userId": "${__Random(1,10000)}"
     }
     ```
   - `__Random(1,10000)` 是 JMeter 函数，生成随机用户 ID

3. **添加 HTTP Header Manager**
   - Content-Type: application/json

4. **添加监听器**（查看结果）
   - **View Results Tree**：查看每个请求的详细响应
   - **Summary Report**：查看汇总统计
   - **Aggregate Report**：查看聚合数据

### 3. 运行测试

1. 先调用预热接口：`POST /api/seckill/warmup/1001`
2. 点击 JMeter 的绿色"启动"按钮
3. 观察监听器中的数据

---

## 简历指标（面试必问）

### 你应该记录这些数据

| 指标 | 说明 | 怎么看 |
|------|------|--------|
| **QPS**（每秒查询数） | 每秒处理多少请求 | Summary Report 的 "Throughput" |
| **平均响应时间** | 请求从发出到返回的平均时间 | Summary Report 的 "Average" |
| **P99 响应时间** | 99% 的请求在多少时间内返回 | Aggregate Report 的 "90% Line" |
| **错误率** | 失败请求的百分比 | Summary Report 的 "Error %" |
| **超卖数量** | 实际卖出的数量是否超过库存 | 查数据库订单数 vs 库存数 |

### 简历写法示例

> **项目**：北部湾海鲜商城 - 秒杀服务
>
> **技术栈**：Spring Boot 3.5 + Redis + RocketMQ + Sentinel + MySQL
>
> **核心工作**：
> - 设计并实现高并发秒杀系统，支持 200+ QPS 的限时抢购场景
> - 使用 Redis Lua 脚本实现原子性库存扣减，保证不超卖
> - 采用 RocketMQ 异步削峰，将瞬间流量转化为队列消息，保护数据库
> - 集成 Sentinel 实现接口限流（200 QPS），防止系统被打爆
> - 数据库唯一索引兜底，防止重复下单
>
> **性能指标**：
> - 压测 QPS：200+（单机）
> - 平均响应时间：< 50ms
> - 错误率：< 1%（限流拒绝除外）
> - 超卖数量：0（1000 次并发测试无超卖）
