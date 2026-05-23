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
