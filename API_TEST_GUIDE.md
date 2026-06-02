# JuPin 后端接口测试文档

> 当前用途：前端未完成、Nginx 未启动时，用 Apifox 直接测试 Spring Boot 后端接口。

## 1. 怎么访问 localhost

不需要前端，也不需要 Nginx。

只要后端 Spring Boot 已启动并监听 8080，Apifox 直接请求：

```text
http://localhost:8080
```

即可访问后端接口，例如：

```text
POST http://localhost:8080/api/auth/login
GET  http://localhost:8080/api/player/pool/list
```

Nginx 只是在前后端分离部署时做反向代理，例如把 `/api` 转发到后端。现在本地接口测试阶段可以完全跳过 Nginx。

## 2. Apifox 导入方式

应用启动后，优先使用 OpenAPI 导入：

```text
http://localhost:8080/v3/api-docs
```

Apifox 操作：

```text
导入项目 / 导入接口
→ OpenAPI / Swagger
→ URL 导入
→ 填入 http://localhost:8080/v3/api-docs
```

如果 URL 导入失败，可以先在浏览器打开 `http://localhost:8080/v3/api-docs`，保存 JSON 后在 Apifox 选择“文件导入”。

## 3. Apifox 环境变量

建议创建一个环境：`local`

| 变量名 | 值 |
|---|---|
| `baseUrl` | `http://localhost:8080` |
| `playerToken` | 登录玩家后填入 |
| `shopToken` | 登录店家后填入 |
| `adminToken` | 管理员登录后填入 |
| `poolId` | 创建拼车后填入 |
| `orderNo` | 创建订单后填入 |
| `shopId` | 创建店铺后填入 |
| `scriptId` | 剧本 ID |

请求 URL 使用：

```text
{{baseUrl}}/api/auth/register
```

需要登录的接口加 Header：

```text
Authorization: Bearer {{playerToken}}
Content-Type: application/json
```

店家端接口使用：

```text
Authorization: Bearer {{shopToken}}
```

管理员接口使用：

```text
Authorization: Bearer {{adminToken}}
```

统一响应格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## 4. 最小冒烟测试顺序

先不要测全部复杂流程，按这个顺序确认服务可用。

### 4.1 注册玩家

```http
POST {{baseUrl}}/api/auth/register
Content-Type: application/json
```

```json
{
  "phone": "13800000001",
  "password": "abc123456",
  "nickname": "玩家一号",
  "gender": 1,
  "role": "player",
  "city": "上海"
}
```

成功后保存：

```text
data.accessToken → playerToken
```

### 4.2 登录玩家

```http
POST {{baseUrl}}/api/auth/login
Content-Type: application/json
```

```json
{
  "phone": "13800000001",
  "password": "abc123456"
}
```

### 4.3 查询玩家信息

```http
GET {{baseUrl}}/api/player/user/me
Authorization: Bearer {{playerToken}}
```

### 4.4 查询剧本列表

```http
GET {{baseUrl}}/api/player/script/list?page=1&size=10
```

该接口目前是公开接口，不需要 token。

### 4.5 创建玩家局

```http
POST {{baseUrl}}/api/player/pool/create
Authorization: Bearer {{playerToken}}
Content-Type: application/json
```

```json
{
  "scriptId": 1,
  "scriptName": "年轮",
  "scriptType": "硬核",
  "roles": "[{\"name\":\"侦探\",\"desc\":\"推理位\"}]",
  "city": "上海",
  "address": "静安区南京西路XX号",
  "startTime": "2026-06-01 14:00:00",
  "endTime": "2026-06-01 18:00:00",
  "maxMembers": 6,
  "price": 88.00,
  "deposit": 10.00,
  "joinType": 1
}
```

成功后保存：

```text
data.id → poolId
```

### 4.6 查询拼车列表

```http
GET {{baseUrl}}/api/player/pool/list?city=上海&page=1&size=10
```

### 4.7 查询拼车详情

```http
GET {{baseUrl}}/api/player/pool/{{poolId}}
```

### 4.8 创建押金订单

```http
POST {{baseUrl}}/api/player/order/create
Authorization: Bearer {{playerToken}}
Content-Type: application/json
```

```json
{
  "poolId": "{{poolId}}",
  "type": 0
}
```

成功后保存：

```text
data.orderNo → orderNo
```

### 4.9 模拟支付

```http
POST {{baseUrl}}/api/player/order/pay/{{orderNo}}
Authorization: Bearer {{playerToken}}
```

### 4.10 查询我的订单

```http
GET {{baseUrl}}/api/player/order/my?page=1&size=10
Authorization: Bearer {{playerToken}}
```

### 4.11 订单创建幂等测试

**目的：** 验证前端重复点击、网络重试时不会创建多条订单。

第一次请求：

```http
POST {{baseUrl}}/api/player/order/create
Authorization: Bearer {{playerToken}}
Content-Type: application/json
```

```json
{
  "poolId": "{{poolId}}",
  "type": 0,
  "idempotentKey": "player-{{poolId}}-deposit-test"
}
```

第二次请求：完全复用同一个 body，再请求一次。

验收点：

- 两次响应 `code` 都是 `200`
- 两次响应的 `data.orderNo` 相同
- 两次响应的 `data.idempotentKey` 相同
- 数据库 `order` 表中同一 `user_id + idempotent_key` 只有一条记录

数据库验证：

```sql
SELECT user_id, idempotent_key, COUNT(*)
FROM `order`
WHERE idempotent_key = 'player-{{poolId}}-deposit-test'
GROUP BY user_id, idempotent_key;
```

### 4.12 重复支付幂等测试

**目的：** 验证同一个订单重复支付时不会重复占座或重复改状态。

连续请求两次：

```http
POST {{baseUrl}}/api/player/order/pay/{{orderNo}}
Authorization: Bearer {{playerToken}}
```

验收点：

- 两次响应 `code` 都是 `200`
- 订单状态保持 `status=1(已支付)`
- `pool_member.status` 从 `1(待支付)` 变为 `2(已加入)`
- `car_pool.current_members` 等于正式成员数，而不是简单自增后的漂移值

数据库验证：

```sql
SELECT status, pay_time, pay_request_no
FROM `order`
WHERE order_no = '{{orderNo}}';

SELECT status
FROM pool_member
WHERE pool_id = {{poolId}} AND user_id = {{playerUserId}};

SELECT current_members
FROM car_pool
WHERE id = {{poolId}};

SELECT COUNT(*) AS joined_count
FROM pool_member
WHERE pool_id = {{poolId}} AND status = 2;
```

### 4.13 拼车详情缓存测试

**目的：** 验证拼车详情接口使用 Redis Cache Aside，热点详情缓存依赖写后主动失效，并通过布隆过滤器拦截不存在 ID。

第一次请求：

```http
GET {{baseUrl}}/api/player/pool/{{poolId}}
Authorization: Bearer {{playerToken}}
```

Redis 验证：

```bash
redis-cli -p 6380 GET pool:detail:{{poolId}}
```

验收点：

- 第一次查询后 Redis 中存在 `pool:detail:{{poolId}}`
- 再次查询详情时接口仍正常返回
- 执行支付、加入、退出、取消、确认、改价等写操作后，`pool:detail:{{poolId}}` 会被删除

布隆过滤器穿透测试：

```http
GET {{baseUrl}}/api/player/pool/999999
Authorization: Bearer {{playerToken}}
```

```bash
redis-cli -p 6380 GET pool:detail:999999
redis-cli -p 6380 EXISTS pool:detail:bloom
```

验收点：

- 接口返回“拼车不存在”
- Redis 不写入 `pool:detail:999999` 空值缓存
- Redis 中存在布隆过滤器 key：`pool:detail:bloom`

### 4.14 RabbitMQ 订单超时测试

**目的：** 验证死信队列可以处理订单超时，不依赖主交易链路同步等待。

测试前注意：订单押金、订单尾款、拼车开始、确认兜底已拆分为不同延迟队列，避免长 TTL 的拼车开始消息阻塞短 TTL 的订单超时测试消息。

RabbitMQ 管理页：

```text
http://localhost:15672
guest / guest
```

确认存在：

```text
timeout.delay.exchange
timeout.order.deposit.delay.queue
timeout.order.final.delay.queue
timeout.pool.start.delay.queue
timeout.confirm.delay.queue
timeout.dlx.exchange
timeout.queue
```

手工投递测试消息：

- Exchange：`timeout.delay.exchange`
- Routing key：`timeout.order.deposit.delay.routing`
- Payload：

```json
{
  "type": "ORDER_DEPOSIT_PAYMENT",
  "orderId": 1,
  "poolId": 1,
  "userId": 1
}
```

Properties 设置：

```text
expiration = 5000
```

验收点：

- 5 秒后消息进入死信消费队列并被消费
- 如果订单仍是 `status=0(待支付)`，会被标记为 `status=4(逾期)`
- 押金单逾期时，成员待支付状态回退为退出
- 尾款单逾期时，用户信用分扣 10

数据库验证：

```sql
SELECT id, type, status, expire_time
FROM `order`
WHERE id = 1;

SELECT id, user_id, credit_score
FROM `user`
WHERE id = 1;
```

### 4.15 超时通知链路测试

**目的：** 验证 `TimeoutConsumer` 在超时状态变更成功后，会写入站内消息，并向 `/topic/pool/{poolId}` 推送刷新事件；重复超时消息不会重复写通知。

测试前准备：

- 后端已启动，RabbitMQ / MySQL / Redis 正常。
- 按 4.1-4.8 创建一个新的玩家局和待支付押金订单，记录：

```text
playerUserId = 24
poolId = 12
depositOrderId = 6
```

> 注意：如果要测试“延迟队列 TTL 到期”，从 `timeout.delay.exchange` 投递，并确保对应延迟队列中没有更长 TTL 消息排在队头。RabbitMQ per-message TTL 在同一个队列内仍有队头阻塞特性。
> 如果本节只验证“消费者通知链路”，可直接投递到 `timeout.dlx.exchange`，模拟 TTL 到期后的死信入队。

#### 4.15.1 押金逾期通知

RabbitMQ 管理页手工投递：

- Exchange：`timeout.dlx.exchange`
- Routing key：`timeout.routing`
- Payload：

```json
{
  "type": "ORDER_DEPOSIT_PAYMENT",
  "orderId": 6,
  "poolId": 12,
  "userId": 24
}
```

数据库验证：

```sql
SELECT id, type, status, pool_id, user_id
FROM `order`
WHERE id = 6;

SELECT id, pool_id, user_id, status, leave_time
FROM pool_member
WHERE pool_id = 12 AND user_id = 24;

SELECT id, msg_key, user_id, type, title, content, related_id
FROM message
WHERE msg_key = 'timeout_deposit_6_24';
```

验收点：

- 订单 `status = 4`
- 成员 `status = 3`
- 站内消息存在且只有一条：`timeout_deposit_6_24`
- 订阅 `/topic/pool/12` 时，可收到 `DEPOSIT_PAYMENT_OVERDUE`

重复投递同一条 payload，再执行：

```sql
SELECT msg_key, COUNT(*) AS cnt
FROM message
WHERE msg_key = 'timeout_deposit_6_24'
GROUP BY msg_key;
```

验收点：

- `cnt = 1`
- 订单和成员状态不回滚、不重复变化

#### 4.15.2 拼车开始超时取消通知

准备一个 `OPEN` 且 `current_members = 0` 的测试拼车；如需缩短验证时间，可在测试库手工把开始时间改到过去：

```sql
UPDATE car_pool
SET start_time = '2026-06-01 14:00:00'
WHERE id = 12;
```

RabbitMQ 管理页手工投递：

- Exchange：`timeout.dlx.exchange`
- Routing key：`timeout.routing`
- Payload：

```json
{
  "type": "POOL_START",
  "poolId": 12
}
```

数据库验证：

```sql
SELECT id, status, current_members, start_time, owner_id
FROM car_pool
WHERE id = 12;

SELECT id, msg_key, user_id, type, title, content, related_id
FROM message
WHERE msg_key = 'timeout_pool_start_12_24';
```

验收点：

- 拼车 `status = 4`
- 发布人站内消息存在且只有一条：`timeout_pool_start_12_24`
- 订阅 `/topic/pool/12` 时，可收到 `POOL_START_TIMEOUT_CANCELLED`

重复投递同一条 payload 后验证：

```sql
SELECT msg_key, COUNT(*) AS cnt
FROM message
WHERE msg_key = 'timeout_pool_start_12_24'
GROUP BY msg_key;
```

验收点：

- `cnt = 1`
- 拼车仍为 `status = 4`

RabbitMQ 队列验证：

```bash
curl -u guest:guest http://localhost:15672/api/queues/%2F/timeout.queue
```

验收点：

- `messages = 0`
- `messages_unacknowledged = 0`

## 5. 店家端最小测试顺序

### 5.1 注册店家账号

```http
POST {{baseUrl}}/api/auth/register
Content-Type: application/json
```

```json
{
  "phone": "13900000001",
  "password": "abc123456",
  "nickname": "店家一号",
  "gender": 0,
  "role": "shop",
  "city": "上海"
}
```

保存：

```text
data.accessToken → shopToken
```

### 5.2 创建店铺

```http
POST {{baseUrl}}/api/shop/create
Authorization: Bearer {{shopToken}}
Content-Type: application/json
```

```json
{
  "name": "静安剧本杀馆",
  "address": "上海市静安区南京西路XXX号",
  "phone": "021-12345678",
  "logo": "",
  "cover": "",
  "description": "主打硬核推理和情感本",
  "openingHours": "10:00-22:00",
  "city": "上海"
}
```

保存：

```text
data.id → shopId
```

### 5.3 查询我的店铺

```http
GET {{baseUrl}}/api/shop/my
Authorization: Bearer {{shopToken}}
```

### 5.4 查询系统剧本库

```http
GET {{baseUrl}}/api/shop/script/list?page=1&size=10
Authorization: Bearer {{shopToken}}
```

### 5.5 添加剧本到店铺

```http
POST {{baseUrl}}/api/shop/script/{{shopId}}/scripts/add
Authorization: Bearer {{shopToken}}
Content-Type: application/json
```

```json
{
  "scriptId": 1,
  "price": 88.00
}
```

### 5.6 发布店家局

```http
POST {{baseUrl}}/api/shop/pool/create
Authorization: Bearer {{shopToken}}
Content-Type: application/json
```

```json
{
  "shopId": "{{shopId}}",
  "scriptId": 1,
  "scriptName": "年轮",
  "scriptType": "硬核",
  "city": "上海",
  "address": "静安区南京西路XX号",
  "startTime": "2026-06-02 14:00:00",
  "endTime": "2026-06-02 18:00:00",
  "maxMembers": 6,
  "price": 88.00,
  "deposit": 10.00,
  "joinType": 1
}
```

## 6. 全量接口清单

### 6.1 认证

| 方法 | 路径 | 登录 | 说明 |
|---|---|---|---|
| POST | `/api/auth/register` | 否 | 注册，返回 token |
| POST | `/api/auth/login` | 否 | 登录，返回 token |
| POST | `/api/auth/logout` | 是 | 退出登录 |
| POST | `/api/auth/refresh` | RefreshToken | 刷新 Access Token |

### 6.2 玩家端

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/player/user/me` | 当前玩家信息 |
| PUT | `/api/player/user/me` | 修改玩家信息 |
| GET | `/api/player/preference` | 我的玩家偏好 |
| PUT | `/api/player/preference` | 保存玩家偏好 |
| GET | `/api/player/script/list` | 系统剧本库 |
| GET | `/api/player/shop/list` | 玩家端店铺列表 |
| GET | `/api/player/shop/{shopId}` | 玩家端店铺主页 |
| GET | `/api/player/shop/{shopId}/scripts` | 玩家查看店铺剧本库 |
| GET | `/api/player/shop/{shopId}/pools` | 玩家查看店铺下全部店家局 |
| POST | `/api/player/pool/create` | 发布玩家局 |
| PUT | `/api/player/pool/{poolId}/price` | 修改价格，Body 直接传数字，如 `99.00` |
| PUT | `/api/player/pool/{poolId}/transfer-dm` | 转让 DM，Body 直接传用户 ID，如 `2` |
| GET | `/api/player/pool/list` | 拼车列表 |
| GET | `/api/player/pool/{poolId}` | 拼车详情 |
| POST | `/api/player/pool/{poolId}/join` | 加入拼车 |
| POST | `/api/player/pool/{poolId}/leave` | 退出/跳车 |
| POST | `/api/player/pool/{poolId}/approve/{userId}` | 通过申请 |
| POST | `/api/player/pool/{poolId}/reject/{userId}` | 拒绝申请 |
| POST | `/api/player/pool/{poolId}/complete` | 发起 COMPLETED 确认 |
| POST | `/api/player/pool/{poolId}/confirm` | 提交确认 |
| POST | `/api/player/pool/{poolId}/finish` | 发起 FINISHED 确认 |
| GET | `/api/player/pool/{poolId}/members` | 成员列表 |
| POST | `/api/player/pool/{poolId}/role/select` | 选择剧本角色 |
| GET | `/api/player/pool/{poolId}/roles` | 角色选择状态 |
| POST | `/api/player/order/create` | 创建订单 |
| POST | `/api/player/order/pay/{orderNo}` | 模拟支付 |
| GET | `/api/player/order/my` | 我的订单 |
| GET | `/api/player/credit/score` | 我的信用分 |
| GET | `/api/player/credit/log` | 信用分流水 |
| POST | `/api/player/review/create` | 提交评价 |
| GET | `/api/player/review/my-dm` | 我作为 DM 收到的评价 |
| GET | `/api/player/message/list` | 消息列表 |
| GET | `/api/player/message/unread-count` | 未读数 |
| PUT | `/api/player/message/read/{msgId}` | 标记单条已读 |
| PUT | `/api/player/message/read-all` | 全部已读 |
| GET | `/api/player/chat/history` | 群聊历史 |
| POST | `/api/player/chat/send` | 发送群聊消息 |

### 6.3 店家端

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/shop/user/me` | 当前店家账号信息 |
| PUT | `/api/shop/user/me` | 修改店家账号信息 |
| GET | `/api/shop/script/list` | 系统剧本库 |
| GET | `/api/shop/script/{shopId}/scripts` | 店铺剧本库 |
| POST | `/api/shop/script/{shopId}/scripts/add` | 添加剧本到店铺 |
| DELETE | `/api/shop/script/{shopId}/scripts/{scriptId}` | 移除店铺剧本 |
| POST | `/api/shop/create` | 创建店铺 |
| GET | `/api/shop/my` | 我的店铺 |
| GET | `/api/shop/current` | 当前店家账号绑定店铺 |
| PUT | `/api/shop/update` | 修改店铺 |
| GET | `/api/shop/search` | 搜索店铺 |
| POST | `/api/shop/join` | 加入店铺 |
| GET | `/api/shop/{shopId}/members` | 店铺成员 |
| POST | `/api/shop/{shopId}/members/add` | 添加成员 |
| POST | `/api/shop/{shopId}/members/remove` | 移除成员，参数 `userId` |
| PUT | `/api/shop/{shopId}/members/role` | 设置成员角色 |
| POST | `/api/shop/pool/create` | 发布店家局 |
| GET | `/api/shop/pool/list` | 店家局列表 |
| GET | `/api/shop/pool/{poolId}` | 店家局详情 |
| POST | `/api/shop/pool/{poolId}/assign-dm` | 指派 DM |
| POST | `/api/shop/pool/{poolId}/complete` | 发起 COMPLETED 确认 |
| POST | `/api/shop/pool/{poolId}/confirm` | 提交确认 |
| POST | `/api/shop/pool/{poolId}/finish` | 发起 FINISHED 确认 |
| GET | `/api/shop/pool/{poolId}/members` | 成员列表 |
| GET | `/api/shop/order/list` | 店铺订单 |
| GET | `/api/shop/review/my` | 店铺评价 |
| GET | `/api/shop/chat/history` | 群聊历史 |
| POST | `/api/shop/chat/send` | 发送群聊消息 |

## 6.5 前端 V0 待补齐接口清单

这些接口是 Web V0 前后端联调需要的后端契约。当前代码如果没有实现，明天应优先补齐；如果已有相近接口，应按这里统一路径、参数和返回结构。

### 玩家偏好

```http
GET {{baseUrl}}/api/player/preference
Authorization: Bearer {{playerToken}}
```

不存在偏好时也返回成功，`data` 中字段可为空：

```json
{
  "city": null,
  "scriptType": null,
  "priceMin": null,
  "priceMax": null,
  "timeSlot": null,
  "minMembers": null,
  "maxMembers": null
}
```

```http
PUT {{baseUrl}}/api/player/preference
Authorization: Bearer {{playerToken}}
Content-Type: application/json
```

```json
{
  "city": "上海",
  "scriptType": "硬核",
  "priceMin": 60.00,
  "priceMax": 120.00,
  "timeSlot": "WEEKEND_NIGHT",
  "minMembers": 4,
  "maxMembers": 6
}
```

验收点：

- 只能操作当前登录玩家自己的偏好
- `scriptType` V0 单选
- `GET /api/player/pool/list?recommend=true` 使用偏好做排序加权，不做硬过滤
- 店家局不进入玩家局推荐池

### 玩家端店铺浏览

```http
GET {{baseUrl}}/api/player/shop/list?page=1&size=10&city=上海&keyword=剧本
Authorization: Bearer {{playerToken}}
```

响应记录至少包含：

```json
{
  "id": 1,
  "name": "静安剧本杀馆",
  "city": "上海",
  "address": "静安区南京西路XX号",
  "phone": "021-12345678",
  "logo": "",
  "cover": "",
  "description": "沉浸式剧本杀体验馆",
  "openingHours": "10:00-22:00",
  "rating": null,
  "ratingText": "暂无评分"
}
```

```http
GET {{baseUrl}}/api/player/shop/{{shopId}}
Authorization: Bearer {{playerToken}}
```

```http
GET {{baseUrl}}/api/player/shop/{{shopId}}/scripts?page=1&size=10
Authorization: Bearer {{playerToken}}
```

```http
GET {{baseUrl}}/api/player/shop/{{shopId}}/pools?page=1&size=10
Authorization: Bearer {{playerToken}}
```

验收点：

- 店铺评分 V0 可返回 `null`，前端展示“暂无评分”
- 店铺列表不需要返回当前店家局数量
- 店铺下店家局展示全部状态，V0 不排序

### 店家当前绑定店铺

```http
GET {{baseUrl}}/api/shop/current
Authorization: Bearer {{shopToken}}
```

```json
{
  "id": 1,
  "name": "静安剧本杀馆",
  "city": "上海",
  "address": "静安区南京西路XX号",
  "role": 1
}
```

验收点：

- V0 默认店家账号已绑定店铺
- 未绑定时返回业务错误：`当前账号未绑定店铺`
- 店家创建店家局时必须校验当前用户是该店店长或管理员
- 店家局创建时后端强制 `joinType=1`
- 店家局创建时 `dmId` 必填，且 DM 必须属于当前店铺成员

### 群聊历史与发送

```http
GET {{baseUrl}}/api/player/chat/history?poolId={{poolId}}&page=1&size=50
Authorization: Bearer {{playerToken}}
```

```http
POST {{baseUrl}}/api/player/chat/send
Authorization: Bearer {{playerToken}}
Content-Type: application/json
```

```json
{
  "poolId": "{{poolId}}",
  "content": "大家几点到？"
}
```

```http
GET {{baseUrl}}/api/shop/chat/history?poolId={{poolId}}&page=1&size=50
Authorization: Bearer {{shopToken}}
```

```http
POST {{baseUrl}}/api/shop/chat/send
Authorization: Bearer {{shopToken}}
Content-Type: application/json
```

历史记录字段：

```json
{
  "id": 1,
  "poolId": 1,
  "senderId": 2,
  "senderName": "推理迷",
  "senderRole": "player",
  "content": "大家几点到？",
  "createTime": "2026-06-01 13:30:00"
}
```

验收点：

- V0 只支持文本
- 正式加入拼车后才能查看和发送
- `FINISHED(3)` 或 `CANCELLED(4)` 后可查看历史，但不能发送
- 建议新增 `chat_message` 表，不复用系统通知 `message` 表

### 通知列表

继续使用现有玩家消息接口：

```http
GET {{baseUrl}}/api/player/message/list?page=1&size=20
Authorization: Bearer {{playerToken}}
```

V0 至少覆盖支付提醒、成团提醒、确认提醒、评价提醒。店家端通知列表不是 V0 阻塞项。

### 6.4 管理端

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/admin/script/create` | 创建剧本 |
| PUT | `/api/admin/script/{scriptId}` | 修改剧本 |
| DELETE | `/api/admin/script/{scriptId}` | 下架/删除剧本 |
| GET | `/api/admin/script/list` | 剧本列表 |
| GET | `/api/admin/user/list` | 用户列表 |
| PUT | `/api/admin/user/{userId}/status` | 修改用户状态 |

## 7. 常用请求体

### 修改用户信息

```json
{
  "nickname": "新昵称",
  "avatar": "",
  "gender": 1,
  "city": "上海",
  "preference": "硬核,情感"
}
```

### 提交确认

```json
{
  "confirmed": true
}
```

### 评价

```json
{
  "poolId": 1,
  "targetId": 1,
  "type": 1,
  "score": 5,
  "content": "体验很好",
  "tags": "准时,逻辑清晰"
}
```

### 指派 DM

```json
{
  "poolId": 1,
  "dmId": 2
}
```

### 设置店铺成员角色

```json
{
  "userId": 3,
  "role": 2
}
```

### 创建/修改剧本

```json
{
  "name": "年轮",
  "type": "硬核",
  "difficulty": 2,
  "minPlayers": 4,
  "maxPlayers": 6,
  "duration": 240,
  "roles": "[{\"name\":\"侦探\"}]",
  "cover": "",
  "priceRef": 88.00,
  "description": "硬核推理本"
}
```

## 8. 注意事项

1. Apifox 请求后端不需要前端项目，也不需要 Nginx。
2. 如果浏览器能打开 `http://localhost:8080/swagger-ui.html` 或 `http://localhost:8080/v3/api-docs`，Apifox 一般也能导入。
3. 如果 Apifox 配了系统代理，访问 localhost 可能走代理导致失败。给 Apifox 设置不代理 `localhost, 127.0.0.1`。
4. 当前 `SecurityConfig` 放行了 Spring Security，但项目还有 `JwtAuthInterceptor`，除公开接口外仍应带 `Authorization: Bearer token`。
5. 玩家端接口必须用玩家 token；店家端接口必须用店家 token；管理端接口必须用管理员 token。
6. 如果返回 401，先检查 token 是否填入环境变量，Header 是否是 `Bearer {{playerToken}}`。
7. 如果返回 500，优先看后端控制台日志，通常是数据库表结构、初始化数据或业务前置条件不满足。
