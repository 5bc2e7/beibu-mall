# 04 API 接口规范

## 1. 通用约定

- 风格：RESTful，JSON 交互，UTF-8。
- 路径前缀：网关按服务名路由，如 `/api/order/**` → order-service。
- 鉴权：除白名单（登录/注册/商品浏览/搜索）外，Header 带 `Authorization: Bearer <JWT>`。
- 版本：路径含 `/v1/`，便于演进。
- 分页：`pageNum`(从1起) + `pageSize`，返回 `{ records, total, pages, current }`。

## 2. 统一响应体（放 mall-common）

```json
{ "code": 200, "msg": "success", "data": { } }
```
```java
public class Result<T> {
    private int code;       // 200成功；4xx客户端；5xx服务端；自定义业务码见下
    private String msg;
    private T data;
    public static <T> Result<T> ok(T data){...}
    public static Result<?> fail(int code, String msg){...}
}
```

**业务错误码段位**（自定义，便于定位服务）：
| 段 | 服务 | 例 |
|---|---|---|
| 1000x | 通用/参数 | 10001 参数错误 |
| 1001x | 用户 | 10011 用户名已存在，10012 密码错误 |
| 1002x | 商品 | 10021 商品已下架 |
| 1003x | 库存 | 10031 库存不足 |
| 1005x | 订单 | 10051 订单不存在，10052 状态非法 |
| 1006x | 支付 | 10061 重复回调（幂等命中） |
| 1008x | 秒杀 | 10081 活动未开始，10082 已售罄，10083 重复抢购 |

## 3. 核心接口清单（逐服务，省略非核心）

> 完整字段以 03 的表与 DTO 为准；这里给路径、方法、语义，供 AI 生成 Controller。

### user-service
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/user/v1/register` | 注册 |
| POST | `/api/user/v1/login` | 登录，返回 JWT |
| GET  | `/api/user/v1/me` | 当前用户信息 |
| GET/POST/PUT/DELETE | `/api/user/v1/address` | 地址 CRUD |
| (Feign) | `getUserById(id)` / `getDefaultAddress(userId)` | 供 order 调用 |

### product-service
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/product/v1/categories` | 分类树 |
| GET | `/api/product/v1/spu/page` | 商品分页(可按类目) |
| GET | `/api/product/v1/spu/{id}` | 详情(含 SKU) |
| POST/PUT | `/api/product/v1/spu`（管理员） | 增改 + 上下架 |
| (Feign) | `getSkuById(skuId)` / `listSkuByIds(ids)` | 供 cart/order 调用 |

### inventory-service（多为内部 Feign）
| 方法 | 说明 |
|---|---|
| (Feign) `lock(orderNo, items)` | 预占库存（乐观锁），返回成功/失败 |
| (Feign) `unlock(orderNo)` | 释放预占（取消/超时） |
| (Feign) `deduct(orderNo)` | 预占转实扣（支付成功） |
| GET `/api/inventory/v1/{skuId}` | 查可用库存 |

### cart-service
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/cart/v1/items` | 加购 {skuId, qty} |
| GET  | `/api/cart/v1` | 购物车列表(联商品服务补全信息) |
| PUT  | `/api/cart/v1/items/{skuId}` | 改数量 |
| DELETE | `/api/cart/v1/items/{skuId}` | 删除 |

### order-service
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/order/v1` | 下单（@GlobalTransactional），返回 orderNo |
| GET  | `/api/order/v1/page` | 我的订单分页 |
| GET  | `/api/order/v1/{orderNo}` | 订单详情 |
| POST | `/api/order/v1/{orderNo}/cancel` | 取消并释放库存 |
| (MQ) | 消费"支付成功" / 消费"超时取消" | 状态流转 |

### payment-service
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/payment/v1/pay` | 发起支付 {orderNo} |
| POST | `/api/payment/v1/notify` | 第三方回调（验签+幂等） |
| GET  | `/api/payment/v1/{orderNo}` | 查支付状态 |

### search-service
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/search/v1/products` | 关键词+筛选+排序+高亮 |
| (MQ) | 消费商品变更 → 同步 ES | 索引更新 |

### seckill-service
| 方法 | 路径 | 说明 |
|---|---|---|
| GET  | `/api/seckill/v1/activities` | 进行中/即将开始的场次 |
| POST | `/api/seckill/v1/{activityId}/buy` | 秒杀下单（限流+Lua+MQ） |
| GET  | `/api/seckill/v1/result/{token}` | 轮询秒杀结果（异步落库后查） |

## 4. 接口示例（下单）

**请求** `POST /api/order/v1`
```json
{ "addressId": 12, "items": [ {"skuId": 1001, "quantity": 2} ], "couponId": null }
```
**响应**
```json
{ "code": 200, "msg": "success", "data": { "orderNo": "20260602xxx", "payAmount": 196.00 } }
```
**失败（库存不足）**
```json
{ "code": 10031, "msg": "库存不足: skuId=1001", "data": null }
```

## 5. 文档化

每个服务集成 **Knife4j(SpringDoc OpenAPI 3)**，启动后 `http://host:port/doc.html` 可看在线文档；
Controller 用 `@Operation`/`@Schema` 标注。网关可聚合各服务文档（可选）。
