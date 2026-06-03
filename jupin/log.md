# 项目日志

## 日期：2026-06-02

### 本轮操作：落地 Mock 支付回调幂等模型

#### 1. 实现内容

- 新增 `payment_event` 支付事件流水表，用于记录 Mock 支付回调事件。
- 新增 `PaymentEvent` 实体和 `PaymentEventMapper`。
- 新增 `MockPayCallbackRequest`，提供显式 Mock 支付回调入口：
  - `POST /api/player/order/mock-callback`
- 现有 `POST /api/player/order/pay/{orderNo}` 保持兼容，内部生成 `payRequestNo`、`callbackRequestNo`、`channelTxnId`，并走同一套 Mock 回调逻辑。
- 新增 `OrderStateMachine`，集中封装订单状态流转：
  - `PENDING -> PAID`
  - `PENDING -> OVERDUE`
  - `PAID -> REFUNDED`
  - `release_status 0 -> 1`
- `TimeoutConsumer` 的订单逾期更新改为调用 `OrderStateMachine.markOverdue()`。

#### 2. 幂等策略

- 创建订单：继续使用 `user_id + idempotent_key` 唯一索引。
- Mock 回调：使用 `payment_event.event_key`、`request_no`、`channel_txn_id` 唯一索引约束重复回调。
- 支付成功：订单状态机使用 `where status = PENDING` 条件更新，重复回调不会重复改订单。
- 超时后回调：订单已 `OVERDUE` 时，成功回调只记录为 `payment_event.status=2(已忽略)`，不回滚订单状态。

#### 3. 文档

- 更新 `API_TEST_GUIDE.md`，新增 Mock 支付回调幂等、重复回调和超时后回调测试步骤。
- 更新 `剧本杀拼车系统_项目文档.md`，补充支付事件流水和订单状态机说明。

### 本轮操作：补充支付占座 MySQL 原子名额兜底并重构面试文档

#### 1. 实现内容

- `OrderServiceImpl.payDeposit()` 在 Redisson 锁内增加 MySQL 原子名额更新：
  - `current_members = current_members + 1`
  - 条件包含 `current_members < max_members`
  - 更新失败时直接按拼车已满处理，事务回滚。
- 保留订单 `PENDING -> PAID`、成员 `PENDING_PAYMENT -> JOINED` 条件更新。
- 保留支付成功后按正式成员数反算 `current_members`，避免简单自增造成计数漂移。

#### 2. 设计目的

- Redisson 分布式锁负责串行化同一拼车下的支付占座请求。
- MySQL 条件更新作为数据库最终兜底，降低锁异常、重复请求或并发竞争导致超员的风险。

#### 3. 文档

- 重构 `面试.md`，将 Redis 缓存、RabbitMQ 超时、超时通知、支付占座并发控制等内容更新为当前升级后的实现口径。

### 本轮操作：升级拼车详情缓存防护

#### 1. 实现内容

- 拼车详情缓存从“短 TTL + 空值缓存”升级为“热点 Key 长期缓存 + 写后主动失效 + 布隆过滤器拦截不存在 ID”。
- 新增 Redis Key：`pool:detail:bloom`，用于记录已存在过的 `poolId`。
- 应用启动时加载 `car_pool.id` 初始化布隆过滤器；创建新拼车成功后立即将新 `poolId` 加入布隆过滤器。
- `PoolServiceImpl.getDetail(poolId)` 查询流程改为：
  - 先查布隆过滤器，判断一定不存在时直接返回“拼车不存在”；
  - 可能存在时再读 `pool:detail:{poolId}`；
  - 缓存未命中时查 MySQL，存在则写入 Redis 且不设置物理 TTL；
  - MySQL 不存在时直接返回，不再写 `__NULL__` 空值缓存。

#### 2. 设计取舍

- 热点拼车详情不再依赖固定 TTL 过期，降低热点 Key 过期瞬间的数据库压力。
- 数据新鲜度依赖已有写操作后的 `evictPoolDetail(poolId)` 主动失效。
- 布隆过滤器存在误判可能，误判时仍会查 MySQL 并返回真实结果；不会误杀已加入布隆过滤器的真实拼车 ID。

#### 3. 文档

- 更新 `API_TEST_GUIDE.md` 4.13，将空值缓存测试改为布隆过滤器穿透测试。
- 更新 `剧本杀拼车系统_项目文档.md` 的 Redis Cache Aside 缓存设计说明。

### 本轮操作：补充超时处理通知链路

#### 1. 实现内容

- `TimeoutConsumer` 注入 `MessageService` 和 `SimpMessagingTemplate`。
- 押金逾期在订单 `PENDING -> OVERDUE` 成功后：
  - 将待支付成员置为 `LEFT`；
  - 写入站内消息“押金订单逾期”；
  - 事务提交后推送 `/topic/pool/{poolId}` 的 `DEPOSIT_PAYMENT_OVERDUE` 事件。
- 尾款逾期在订单 `PENDING -> OVERDUE` 成功后：
  - 扣减信用分 10 分；
  - 写入站内消息“尾款逾期”；
  - 事务提交后推送 `FINAL_PAYMENT_OVERDUE` 事件。
- 拼车开始超时取消在 `OPEN -> CANCELLED` 条件更新成功后，给发布人写入站内消息，并推送 `POOL_START_TIMEOUT_CANCELLED` 事件。
- 成团确认/结束确认超时兜底在状态推进成功后，给当前正式成员写入站内消息，并推送确认兜底完成事件。

#### 2. 幂等策略

- 站内消息复用 `message.msg_key` 唯一索引，重复超时消息重复插入会被忽略。
- 通知只在数据库条件更新成功或状态机推进成功后触发。
- WebSocket 推送注册到事务提交后执行，推送失败只记录 warning，不影响超时消费主流程。

#### 3. 文档

- 更新 `剧本杀拼车系统_项目文档.md`，补充超时通知规则，并将“超时处理后的通知链路”从待处理项移入已处理记录。

### 本轮操作：订单幂等、Redis Cache Aside、RabbitMQ 死信超时处理落地

#### 1. 订单幂等机制

**目标：** 解决前端重复点击、网络重试、支付重复请求导致的重复下单和重复支付问题。

**实现内容：**
- `OrderCreateRequest` 增加 `idempotentKey`，创建订单时支持客户端传入业务幂等键。
- `Order` / `OrderVO` 增加 `idempotentKey`、`payRequestNo`、`callbackRequestNo`、`expireTime` 字段。
- `OrderService.create()` 改为按 `userId + idempotentKey` 查询历史订单；重复请求直接返回已有订单。
- 未传 `idempotentKey` 时，后端默认使用 `userId:poolId:type` 作为稳定业务键，兼容旧前端。
- 支付状态更新改为数据库条件更新，仅允许 `PENDING -> PAID`；重复支付命中已支付订单时幂等返回。
- 初始化 SQL 增加 `uk_user_idempotent_key`、`uk_channel_txn_id`、`idx_status_expire` 等索引。

#### 2. Redis Cache Aside 缓存

**目标：** 降低拼车详情高频读取对 MySQL 的压力，并缓解不存在 ID 带来的缓存穿透。

**实现内容：**
- 新增 Redis Key 常量：`pool:detail:` 和空值占位 `__NULL__`。
- `PoolServiceImpl.getDetail(poolId)` 改造为 Cache Aside：
  - 先读 Redis；
  - 命中 JSON 直接返回；
  - 命中空值缓存直接返回不存在；
  - 未命中查 MySQL；
  - 存在数据写入 Redis，TTL 为 10 分钟 + 随机抖动；
  - 不存在数据写入 60 秒空值缓存。
- 拼车相关写操作后删除 `pool:detail:{poolId}`，包括加入、退出、取消、审核、支付成功、改价、DM 指派/转让、确认流程、角色选择等。

#### 3. RabbitMQ 死信超时处理

**目标：** 将订单超时、拼车超时、确认超时从主交易链路中解耦，避免依赖同步定时扫描。

**实现内容：**
- `RabbitConfig` 新增延迟交换机、延迟队列、死信交换机和超时消费队列：
  - `timeout.delay.exchange`
  - `timeout.delay.queue`
  - `timeout.dlx.exchange`
  - `timeout.queue`
- 新增 `TimeoutMessage`、`TimeoutProducer`、`TimeoutConsumer`。
- 创建订单后投递支付超时消息：
  - 押金订单 15 分钟未支付：订单标记逾期，成员从待支付回退为退出。
  - 尾款订单 24 小时未支付：订单标记逾期，信用分扣 10。
- 创建拼车后投递开局超时消息：到开始时间仍为 `OPEN` 且无人正式加入时自动取消。
- 发起成团确认/结束确认后投递确认超时消息：消费端重新查库，满足条件才推进状态。
- 消费端所有处理都先查库再条件更新，重复消息可幂等跳过。

#### 4. 项目文档与 SQL

**文档更新：**
- `剧本杀拼车系统_项目文档.md` 增加 v3.0 版本记录。
- 补充订单幂等、Cache Aside、RabbitMQ 死信队列设计和测试说明。
- 将后续优化章节中的对应模块从“待落地”更新为“已落地 + 后续优化”。

**SQL 更新：**
- 同步更新根目录 `sql/init.sql` 和 `jupin/sql/init.sql`。
- 订单表增加幂等、支付请求、回调请求、过期时间字段和相关索引。

#### 5. 验证说明

- 用户已在本地完成编译运行验证。
- 本轮曾尝试本地 Maven 编译，但当前环境无 `mvn` 和 Maven Wrapper。
- 曾尝试 Docker 构建验证，初次受 Docker daemon 沙箱权限限制；提权后遇到基础镜像平台 manifest 问题，后续显式平台构建被中断，因此最终以用户本地编译运行为准。

#### 6. 修改文件清单

| 文件 | 修改内容 |
|------|---------|
| `jupin/jupin-pojo/src/main/java/com/jupin/pojo/dto/OrderCreateRequest.java` | 新增订单创建幂等 Key |
| `jupin/jupin-pojo/src/main/java/com/jupin/pojo/entity/Order.java` | 新增幂等、支付请求、回调请求、过期时间字段 |
| `jupin/jupin-pojo/src/main/java/com/jupin/pojo/vo/OrderVO.java` | 返回订单幂等 Key 和过期时间 |
| `jupin/jupin-server/src/main/java/com/jupin/server/service/impl/OrderServiceImpl.java` | 实现创建订单幂等、支付条件更新、订单超时消息投递 |
| `jupin/jupin-server/src/main/java/com/jupin/server/service/impl/PoolServiceImpl.java` | 实现拼车详情 Cache Aside、写后缓存失效、拼车/确认超时消息投递 |
| `jupin/jupin-server/src/main/java/com/jupin/server/config/RabbitConfig.java` | 新增超时延迟队列和死信队列配置 |
| `jupin/jupin-server/src/main/java/com/jupin/server/mq/*` | 新增超时消息、生产者、消费者 |
| `sql/init.sql` / `jupin/sql/init.sql` | 增加订单幂等字段和索引 |
| `剧本杀拼车系统_项目文档.md` | 写入 v3.0 技术方案 |

### 本轮操作：接口测试执行与拼车创建链路修复

#### 1. 问题现象

按 `API_TEST_GUIDE.md` 中 4.11-4.14 的接口测试流程执行时，前置步骤“创建玩家局”返回：

```json
{
  "code": 500,
  "message": "服务器内部错误",
  "data": null
}
```

该问题导致订单幂等、重复支付、缓存和 RabbitMQ 超时测试无法继续执行。

#### 2. 排查过程

| 检查项 | 结果 |
|--------|------|
| 后端端口 | 确认当前 `8080` 返回 `JuPin API` |
| 注册玩家 | 成功 |
| `GET /api/player/user/me` | 成功，说明 token 和 `BaseContext` 正常 |
| 剧本数据 | `script.id=1` 存在且上架 |
| 用户信用分 | 新注册玩家 `credit_score=100` |
| RabbitMQ 交换机/队列 | `timeout.delay.exchange`、`timeout.delay.queue`、`timeout.dlx.exchange`、`timeout.queue` 均存在，绑定关系正确 |

#### 3. 根因判断

排查过程中发现两类问题：

1. 创建拼车主流程末尾会调用 `sendPoolStartTimeout(pool)` 投递拼车开始超时消息。该 MQ 投递属于补偿任务，不应影响拼车创建主交易链路。如果 RabbitMQ 投递阶段出现异常，原实现会让异常继续向外抛出，导致创建拼车接口返回 500，违背“超时任务从主交易链路解耦”的设计目标。
2. `CarPool.roles` 是 `String`，而 `PoolVO.roles` 是 `List<RoleStatusVO>`。`VOConverter.toPoolVO()` 使用 `BeanUtil.copyProperties(pool, PoolVO.class)` 时会尝试把字符串 `roles` 转换为列表字段，导致创建拼车返回 VO 和查询拼车详情时出现 500。

#### 4. 修复内容

1. 修改 `PoolServiceImpl.sendPoolStartTimeout()`：

- 对 `timeoutProducer.send(...)` 增加 `try/catch`
- 投递失败时记录 warning 日志
- 不再阻断拼车创建主流程

2. 修改 `VOConverter.toPoolVO()`：

- `BeanUtil.copyProperties(pool, PoolVO.class, "roles")`
- 忽略实体中的 `roles` 字符串字段，避免自动转换到 `PoolVO.roles`
- 角色状态继续通过 `/api/player/pool/{poolId}/roles` 接口返回

```text
拼车创建成功是主链路结果；
超时消息投递失败是补偿链路问题，只记录日志，后续可由定时扫描或人工补偿兜底。
```

#### 5. 接口测试结果

按 `API_TEST_GUIDE.md` 4.11-4.14 执行测试：

| 测试项 | 结果 | 说明 |
|--------|------|------|
| 4.11 订单创建幂等 | 通过 | 同一 `idempotentKey` 两次创建返回相同 `orderNo` |
| 4.12 重复支付幂等 | 通过 | 同一订单连续支付两次均返回成功，订单保持 `PAID` |
| 4.13 拼车详情缓存 | 通过 | 首次详情查询写入 `pool:detail:{poolId}`；支付后缓存被删除 |
| 4.14 RabbitMQ 订单超时 | 通过 | 清空测试环境延迟队列后，5 秒 TTL 消息进入死信队列并被消费，订单变为 `OVERDUE`，成员变为 `LEFT` |

测试产物示例：

```text
PASS RabbitMQ timeout retest
order.status=4
pool_member.status=3
```

#### 6. RabbitMQ 测试注意事项

当前 `timeout.delay.queue` 使用 per-message TTL。RabbitMQ 的 per-message TTL 存在队头阻塞特性：如果队头是一个长 TTL 的 `POOL_START` 消息，排在后面的 5 秒测试消息不会及时过期。

因此在测试环境手工验证 5 秒超时时，需要先清空 `timeout.delay.queue`，再投递短 TTL 测试消息。生产优化方向是拆分不同业务类型/不同延迟级别的延迟队列，或引入 RabbitMQ delayed message exchange 插件。

### 本轮操作：RabbitMQ 延迟队列拆分优化

#### 1. 优化背景

4.14 接口测试发现，原 `timeout.delay.queue` 使用 RabbitMQ per-message TTL 承接所有超时任务。该模式在单队列下存在队头阻塞：长 TTL 的 `POOL_START` 消息排在队头时，后面的短 TTL 订单测试消息即使已到期，也无法及时进入死信队列。

#### 2. 修复内容

- `RabbitConfig` 将单一延迟队列拆分为 4 类业务延迟队列：
  - `timeout.order.deposit.delay.queue`
  - `timeout.order.final.delay.queue`
  - `timeout.pool.start.delay.queue`
  - `timeout.confirm.delay.queue`
- `TimeoutMessage` 新增 `ORDER_DEPOSIT_PAYMENT`、`ORDER_FINAL_PAYMENT`，区分押金超时和尾款超时。
- `TimeoutProducer` 根据消息类型选择对应 routing key：
  - 押金：`timeout.order.deposit.delay.routing`
  - 尾款：`timeout.order.final.delay.routing`
  - 拼车开始：`timeout.pool.start.delay.routing`
  - 确认兜底：`timeout.confirm.delay.routing`
- `TimeoutConsumer` 继续统一监听 `timeout.queue`，消费端业务处理逻辑不拆散。
- `OrderServiceImpl.create()` 创建订单后按订单类型投递押金或尾款超时消息。

#### 3. 文档同步

- `API_TEST_GUIDE.md` 4.14 已更新为向 `timeout.order.deposit.delay.routing` 投递 5 秒押金超时测试消息。
- `剧本杀拼车系统_项目文档.md` 已将 RabbitMQ 延迟队列拆分写入 1.8.2，并把单延迟队列队头阻塞记录到修正部分。
- 待处理问题新增后续优化项：超时通知链路、尾款自动创建、资金托管释放/退款闭环、Mock 支付回调模型、前端开发/测试入口、后端接口集成测试。

#### 4. 后续验证要求

后端重启后 Spring 会声明新的 RabbitMQ 队列和绑定。重启后按 `API_TEST_GUIDE.md` 4.14 重新投递测试消息，不再需要清空原 `timeout.delay.queue`。

## 日期：2026-05-20

### 项目进度

#### 项目概述

剧本杀拼车系统 (JuPin) — 一个帮助玩家组队拼车玩剧本杀的平台。用户可发布拼车信息（剧本、时间、地点、角色要求），其他用户可申请加入。包含用户认证、拼车 CRUD、订单支付、信用评分、实时聊天、智能匹配等功能。

#### 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 语言 | Java (OpenJDK) | 17.0.19 |
| 框架 | Spring Boot | 2.7.18 |
| ORM | MyBatis-Plus | 3.5.5 |
| 安全 | Spring Security + JWT | 5.7.11 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis (Redisson) | 7 |
| 消息队列 | RabbitMQ | 3.12 |
| 实时通信 | WebSocket (STOMP) | — |
| API 文档 | Knife4j (SpringDoc OpenAPI) | 4.4.0 |
| 工具库 | Hutool | 5.8.25 |
| 构建工具 | Maven | 3.8+ (Wrapper) |

#### 模块结构

```
jupin/
├── jupin-common/          # 公共模块：异常定义、JWT工具类
│   ├── exception/
│   │   └── BaseException.java
│   └── utils/
│       └── JwtUtil.java
├── jupin-pojo/            # 数据模型：实体、DTO、VO
│   ├── dto/               # 请求参数 (LoginRequest, RegisterRequest, OrderCreateRequest 等)
│   ├── entity/            # 数据库实体 (User, CarPool, Order, PoolMember, Message 等)
│   └── vo/                # 返回结果 (LoginVO, UserVO, OrderVO, CreditRankVO 等)
└── jupin-server/          # 应用服务：控制器、服务层、Mapper、配置
    ├── config/            # 配置类 (WebSocket, Redisson, MyBatis-Plus 分页)
    ├── controller/        # 控制器
    │   ├── user/          # UserController, CreditController
    │   ├── pool/          # PoolController
    │   └── order/         # OrderController
    ├── engine/            # 智能匹配引擎 (MatchEngine, MatchTask)
    ├── mapper/            # MyBatis Mapper 接口
    └── service/           # 服务实现
        └── impl/          # UserServiceImpl, PoolServiceImpl, OrderServiceImpl 等
```

#### 已实现功能

1. **用户模块** — 注册（手机号）、登录（JWT）、个人信息查询与修改、账号状态管理
2. **拼车模块** — 发布拼车、浏览拼车列表（分页+筛选）、查看拼车详情、加入/退出拼车
3. **状态机** — 拼车状态流转：开放(0) → 满员(1) → 已完成(2) / 已取消(3)，带并发安全校验
4. **订单模块** — 创建订单（押金/车费）、支付、退款、查看订单列表
5. **信用评分** — 用户初始 100 分，信用分增减、积分流水查询、信用排行榜
6. **评价系统** — 拼车完成后参与者互评，评分影响信用分
7. **消息通知** — 系统消息（加入申请、同意/拒绝通知），已读/未读管理
8. **实时聊天** — 基于 WebSocket STOMP 协议的拼车群聊，仅成员可发送
9. **智能匹配** — 定时任务自动匹配符合条件的开放拼车（同城市+剧本类型+时间窗口）
10. **消息队列** — RabbitMQ 异步处理消息通知

#### 当前状态

项目在 JDK 17 环境下成功编译并启动运行，三个 Docker 中间件服务正常运行，应用可通过 `http://localhost:8080` 访问。API 文档可通过 Knife4j (Swagger UI) 查看。

---

### 问题与解决

#### 1. BeanUtil.copyOptions() 找不到符号

- **问题**：`UserServiceImpl.java:73` 中 `BeanUtil.copyOptions()` 方法在 Hutool 5.8.25 中已被移除，JDK 17 下编译报错。
- **解决**：改为 `CopyOptions.create().ignoreNullValue()`，添加导入 `cn.hutool.core.bean.copier.CopyOptions`。

#### 2. QueryWrapper/UpdateWrapper 泛型推断失败

- **问题**：JDK 17 对菱形运算符 `<>` 的类型推断更严格，`new QueryWrapper<>()` 被推断为 `QueryWrapper<Object>`，无法赋值给需要具体泛型参数的 Mapper 方法。
- **涉及文件**：`ChatServiceImpl.java`、`PoolServiceImpl.java`、`OrderServiceImpl.java` 等 13 个文件。
- **解决**：全部显式声明泛型参数，如 `new QueryWrapper<PoolMember>()`。

#### 3. Redis 连接认证失败 (NOAUTH)

- **问题**：应用启动时报 `RedisAuthRequiredException: NOAUTH Authentication required`，无法连接 Redis。
- **原因**：macOS 上通过 Homebrew 安装的本地 redis-server（PID 774）占用了端口 6379 并设置了密码认证，应用连接时走了本机 Redis 而非 Docker 容器中的 Redis。
- **解决**：调整 Docker 端口映射 `6380:6379`，application.yml 中 Redis 端口改为 6380，避免与本机服务冲突。

#### 4. MySQL 连接失败 (Public Key Retrieval / Access Denied)

- **问题**：应用启动时依次出现 `Public Key Retrieval is not allowed` 和 `Access denied for user 'root'@'localhost'`。
- **原因**：
  - 本机安装了 Oracle MySQL（`/usr/local/mysql/`），抢占端口 3306，导致连接到了本机 MySQL 而非 Docker 容器。
  - MySQL 8.0 默认 `caching_sha2_password` 认证插件需要额外参数。
- **解决**：
  - JDBC URL 追加 `&allowPublicKeyRetrieval=true`。
  - Docker 端口映射改为 `3307:3306`，application.yml 中 MySQL 端口改为 3307。

#### 5. jupin-pojo 模块缺少依赖

- **问题**：jupin-pojo 模块的 pom.xml 未声明任何依赖，但代码中使用了 MyBatis-Plus 注解、Swagger 注解、Validation 注解，编译失败。
- **解决**：在 jupin-pojo/pom.xml 中添加 `mybatis-plus-annotation`、`knife4j-openapi3-spring-boot-starter`、`spring-boot-starter-validation`。

#### 6. 缺少 CreditRankVO 类

- **问题**：`CreditServiceImpl.java` 引用了 `CreditRankVO` 类，但该类未创建。
- **解决**：创建 `CreditRankVO`，包含 `userId`、`nickname`、`score` 三个字段。

#### 7. Docker Compose 网络问题 (中国区)

- **问题**：Docker Hub 在中国网络环境下拉取镜像超时，配置 USTC 镜像站已失效。
- **解决**：配置 Docker daemon 代理（colima.yaml proxy），通过本机代理拉取镜像。

---

### 当前运行状态

| 服务 | 状态 | 连接地址 |
|------|------|----------|
| 应用 (Tomcat) | 运行中 | http://localhost:8080 |
| MySQL 8.0 (Docker) | 健康 | localhost:3307 |
| Redis 7 (Docker) | 健康 | localhost:6380 |
| RabbitMQ 3.12 (Docker) | 健康 | localhost:5672 |

### 下一步计划

第 1 步：检查数据库初始化

先确认 sql/init.sql 是否存在，数据库表是否已创建。没有表结构，接口测不了。

第 2 步：按业务流测试接口

按用户操作顺序测试，链路清晰：

1. 注册 POST /api/user/register → 创建测试账号
2. 登录 POST /api/user/login → 获取 JWT Token
3. 获取用户信息 GET /api/user/me → 验证登录态
4. 发布拼车 POST /api/pool/create → 创建一个拼车
5. 浏览拼车 GET /api/pool/list → 查看拼车列表
6. 加入拼车 POST /api/pool/join → 用第二个账号加入
7. 创建订单 POST /api/order/create → 支付押金
8. 支付订单 POST /api/order/pay → 模拟支付
9. 查看订单 GET /api/order/my → 验证订单状态
10. 信用排行榜 GET /api/credit/rank → 验证评分系统

第 3 步：检查异常流程

- 重复注册同一手机号
- 错误密码登录
- 加入不存在的拼车
- 重复加入同一拼车
- 非车主取消拼车
- 退款操作

第 4 步：修复测试中暴露的问题

接口测试过程中发现的问题逐一修复。 

---

## 日期：2026-05-21 (第二段)

### 本轮操作

#### 1. 全面修审项目文档（v1.0 → v2.0）

**问题背景：** 原项目文档功能设计不严谨，与预期需求存在偏差。通过逐模块提问方式，重新确认了所有核心业务设计。

**修审内容涉及 10 个问题：**

| # | 问题 | 结论 |
|---|------|------|
| 1 | API路径划分 | 按角色分：`/api/player/`（玩家）、`/api/shop/`（店家）、`/api/admin/`（管理员），相同功能各端各自 Controller + 共用 Service |
| 2 | 双向匹配范围 | 仅玩家局适用，店家局为玩家单向选择 |
| 3 | 身份角色定义 | 注册时区分玩家/店家，店家不能注册为玩家 |
| 4 | 信用分与评价分分离 | 信用分=玩家守约记录（店家及其DM不参与），评价分=店家/DM服务质量（过低冻结接单权限），两套独立体系，不做排行榜 |
| 5 | 两个完成状态 | COMPLETED(2)=拼车成功(释放押金)，FINISHED(3)=剧本杀完成(释放车费+开评价) |
| 6 | 完成确认机制 | COMPLETED：全员确认；FINISHED：结束时间前全员，过后多数确认 |
| 7 | 支付流程 | 平台作为中介，两阶段释放：押金→COMPLETED释放，剩余车费→FINISHED释放 |
| 8 | 角色预选 | 不做（极低优先级，记入扩展） |
| 9 | LBS | 仅城市筛选，不做地图和经纬度 |
| 10 | price与deposit关系 | deposit是price的预付部分，不是额外费用 |

**新增模块：**
- 店铺体系（shop + shop_member）：店长/管理员/普通成员三级权限
- 剧本体系（script + shop_script）：系统剧本库 + 店铺剧本库
- 双模式拼车：玩家局 + 店家局
- 管理员后台：剧本管理 + 用户管理

**数据库表从 7 张扩展到 11 张：**
新增 shop、shop_member、script、shop_script
修改 car_pool（加 type/shop_id/script_id/dm_id/status扩展）、review（加 type）、user（加 role/shop_id）

**接口重新规划：**
- 玩家端 26 个接口
- 店家端 28 个接口
- 管理后台 6 个接口
- WebSocket 4 个端点

#### 2. Apifox JSON 更新 (v1.0 → v1.1)

对比代码差异并修正：
- 移除代码中不存在的 3 个接口（match/join、match/leave、match/status）
- 列表接口返回类型从 PageResult 修正为 List
- 字段名对齐 camelCase（creditScore、unreadCount）
- 补充缺失的 Schema（RoleStatusVO、LocationUpdateRequest 等）
- 移除无效的空字符串路径分隔符

#### 3. POJO 注释补齐

为 jupin-pojo 模块下全部 24 个文件添加了字段级注释：
- 7 个 Entity：行尾注释
- 8 个 DTO + 9 个 VO：@Schema 注解

#### 4. 当前确认的前端方案

- 技术栈：Vue 3 + Vant UI + Pinia + Vue Router
- 支付：全部模拟 Mock
- 页面路由：`/player/`（玩家界面）、`/shop/`（店家界面）、`/admin/`（管理后台）

#### 待办

1. 重构后端代码（按新表结构、新接口设计、角色权限拦截器重写）
2. 初始化剧本数据（30-50 个热门剧本写入 script 表）
3. 搭建前端项目（Vue 3 + Vant）
4. 按业务流测试接口

---

## 日期：2026-05-21 (第三段)

### 本轮操作：文档审查与问题修复

#### 1. 第一次专业审查（评分 6.5/10）

由架构师角色对项目文档进行 5 维度系统性审查，识别出以下关键问题：

- **P0（阻塞性）：** 成员确认接口缺失——文档描述了 COMPLETED/FINISHED 需要"全员确认"，但 API 表中没有提供成员调用的 confirm 接口
- **P1（重要）：** 支付流程不完整、安全机制缺失、超时兜底机制缺失、事务边界未定义
- **P2（一般）：** 注册接口重复、路径语义错误、术语混用、缺少元数据、搜索筛选能力不足等 8 项

#### 2. 问题修复（v1.1 → v2.0）

逐个修复审查报告中的全部问题，文档从 6.5 分提升至 8.0 分：

**P0-紧急（3 项）：**
- 新增成员 confirm 接口：`POST .../pool/{id}/confirm`（玩家端 + 店家端）
- 完善确认机制流程文档（发布人发起 → 全员确认 → 状态变更）
- 添加确认接口的请求参数、鉴权规则、幂等性说明

**P1-重要（7 项）：**
- 重写支付流程：两阶段资金流向图、阶段说明表、异常处理策略（重试/超时/幂等）
- 新增超时兜底机制：TIMEOUT(5) 状态 + 3 种超时场景规则 + 定时任务 SQL 示例
- 新增"八、安全设计"：涵盖 BCrypt、JWT 双 Token、限流、输入校验、注入防护
- 新增"九、事务与一致性策略"：本地事务 + MQ 重试 + Redisson 锁策略
- 补充评价分最小样本量规则（≥3 条才触发冻结）

**P2-一般（6 项）：**
- 合并注册接口为 `/api/auth/register` + role 参数
- 修正 join 路径为 `/api/user/shop/join`
- 补充 9 个列表筛选参数（剧本类型/价格/时间/分页/recommend）
- 统一术语："车主"→"发布人"，"主持人"→"DM"
- 文档添加版本元数据表
- match/start 标注弃用（改用 list?recommend=1）

#### 3. 第二次审查（评分 8.0/10）

修复后重新审查，确认 P0 阻塞性问题已全部解决，文档通过审查，可进入开发阶段。

#### 4. 补充修正

- 支付对象修正：玩家局费用归属 DM（非发布人）
- 移除 outbox 消息表设计（Demo 阶段过度设计，改为直接 MQ + @Retryable）
- 分页响应格式修正为项目实际的 `Result<PageResult<T>>`

#### 文档统计数据

| 指标 | 数据 |
|------|------|
| 总行数 | ~770 行 |
| 数据库表 | 11 张（DDL 全） |
| API 接口 | 50+ 个（含公共认证 4 个 + 玩家端 28 个 + 店家端 26 个 + 管理后台 6 个 + WebSocket 4 个） |
| 扩展功能 | 11 项 |

#### 当前项目状态

文档已完成修复并通过审查，具备指导开发的完整度。建议按以下顺序推进：
1. 数据库初始化（DDL + 剧本数据导入）
2. 后端代码重构（按新表结构 + 新 API + 角色拦截器重写）
3. 前端项目搭建（Vue 3 + Vant + 路由）
4. 按业务流联调测试

> ⚠️ **说明：** 旧日志中的"信用排行榜""旧接口""旧状态设计"等不再实现，以项目文档最新版本（当前 v2.4）为准。日志仅作开发过程记录，不反映当前设计方案。

---

## 日期：2026-05-21 (第四段)

### 本轮操作：后端代码审查、接口测试准备与本地运行排查

#### 1. 后端代码审查

按照 15 个维度对当前后端代码进行了审查，覆盖调用链路、功能逻辑、方法设计、代码规范、性能、安全、配置、日志、并发、数据库、缓存、MQ、接口设计等方面。

识别出的主要问题：

- `sql/init.sql` 与当前实体/项目文档不一致，缺少店铺、剧本、店铺成员、店铺剧本等核心表，部分字段与代码实体不匹配
- 拼车人数统计时机不合理：加入/审核通过阶段提前增加 `currentMembers`，与“支付押金后才真正占座”的业务规则不一致
- 订单支付后只更新订单状态，未同步更新 `pool_member` 状态和拼车人数，容易导致订单已支付但成员未入车
- 店家审核通过后直接计入人数，存在未支付用户占座问题
- 完成确认逻辑允许 `OPEN` 状态进入完成确认，状态机边界不够严谨
- 余额支付金额未扣除押金，存在尾款金额计算错误
- 订单创建缺少数据库唯一约束兜底，可能出现重复订单
- Spring Security 放行范围过大，实际鉴权主要依赖 MVC 拦截器，安全边界不够清晰

#### 2. 已完成的后端修复

围绕当前最影响主链路测试的问题进行了修复，暂不做过度设计：

- 调整拼车创建逻辑：发起人初始为 `PENDING_PAYMENT`，不再创建即占用名额
- 调整加入拼车逻辑：玩家加入后进入 `PENDING_PAYMENT` 或 `PENDING_REVIEW`，不再提前增加 `currentMembers`
- 调整店家审核逻辑：审核通过后只进入待支付状态，不再提前占座
- 调整押金支付逻辑：押金支付成功后才将成员改为 `JOINED`，并原子更新拼车人数
- 增加支付阶段的 Redisson 锁，避免多人并发支付导致超员
- 支付后若人数达到上限，将拼车状态从 `OPEN` 推进为 `FULL`
- 修正尾款金额计算：尾款 = 总价 - 押金，避免重复收费
- 修正完成确认规则：只允许 `FULL` 状态进入完成确认
- 修正多数确认逻辑：超过成员总数半数才进入 `FINISHED`
- 修正店铺资料更新逻辑：忽略 null 字段，避免局部更新把已有字段覆盖为空
- 调整 refresh token 策略：刷新接口只返回新的 access token，与当前接口响应保持一致
- 收紧 Security 默认规则：非明确 API 路径默认拒绝访问
- 重写初始化 SQL，使表结构与当前实体、项目文档保持一致，并补充订单唯一约束

#### 3. 接口文档与 Apifox 测试准备

生成了可导入 Apifox 的 OpenAPI 文档：

- 文件位置：`/Users/wangkexin/Desktop/JuPin/apifox-openapi.yaml`
- 覆盖内容：认证、玩家端拼车、订单、评价、消息、店家端、管理端等主要接口
- 目的：在前端未开发、Nginx 未启动的情况下，通过 Apifox 直接访问后端 `http://localhost:8080` 手动测试接口链路

补充说明：

- 前端未启动不影响接口测试
- Nginx 未启动不影响本地后端直连测试
- 本地测试 Base URL 使用：`http://localhost:8080`
- 登录成功后，将 `accessToken` 配置到 Apifox 的 `Authorization: Bearer <token>` 中继续测试受保护接口

#### 4. 本地运行与中间件排查

确认当前项目本地配置：

- 后端端口：`8080`
- MySQL：`localhost:3307`
- Redis：`localhost:6380`
- RabbitMQ：`localhost:5672`

连通性检查结果：

- MySQL `script_murder_carpool` 库可连接，`select 1` 成功
- Redis `PING` 返回 `PONG`
- RabbitMQ `5672` 端口可连接

注意：当前 3307、6380、5672 监听进程显示为 `ssh`，说明本地环境可能通过 SSH 端口转发访问中间件，后续启动项目前需要保持该转发连接有效。

#### 5. 已完成的接口验证

已成功测试用户登录接口，后端返回：

- `code = 200`
- `accessToken` 正常生成
- `refreshToken` 正常生成
- 用户信息正常返回

此前注册接口遇到过 Redisson 与 Spring Data Redis 版本不兼容问题：

- 错误：`NoClassDefFoundError: org/springframework/data/redis/connection/zset/Tuple`
- 原因：Spring Boot 2.7 / Spring Data Redis 2.7 与 `redisson-spring-data-32` 不匹配
- 修复方向：排除 `redisson-spring-data-32`，显式使用 `redisson-spring-data-27`

#### 6. 当前项目状态

今天已完成：

1. 项目文档专业化审查与多轮修正确认
2. 后端代码主链路审查
3. 押金支付占座链路的关键一致性修复
4. 初始化 SQL 与实体结构对齐
5. Apifox OpenAPI 导入文件生成
6. 本地 MySQL、Redis、RabbitMQ 连通性确认
7. 登录接口手动验证通过

#### 7. 下一步建议

下一轮优先按以下顺序继续：

1. 由本地编译确认当前后端代码是否存在编译错误
2. 启动后端服务，按 Apifox 测试主链路：注册/登录 → 创建拼车 → 创建押金订单 → 支付押金 → 查询拼车详情
3. 补充订单创建阶段的成员身份校验，避免非成员直接创建押金订单
4. 补充支付接口的订单归属校验，避免用户通过订单号操作他人订单
5. 继续完善离车、取消、退款、超时释放等异常链路
6. 在主链路稳定后再考虑压测、Redis Lua、MQ 重试、幂等消费等高并发增强点

---

## 日期：2026-05-22

### 本轮操作：命令行模拟 Apifox 完整接口链路测试与问题修复

#### 1. 测试方式

在后端服务运行于 `http://127.0.0.1:8080` 的前提下，使用 `curl` 脚本模拟 Apifox 执行接口测试，自动完成账号注册、登录、Token 保存、业务 ID 保存和链路断言。

最终测试报告：

- 报告文件：`/tmp/jupin-api-test-v2-1779450471/report.md`
- 原始响应目录：`/tmp/jupin-api-test-v2-1779450471`
- 测试结果：`PASS 69 / FAIL 0`

#### 2. 覆盖的基础功能链路

本轮完整覆盖了以下主流程：

1. 公共认证：注册、登录、刷新 Token、重复注册、错误密码、无 Token 访问
2. 权限隔离：玩家访问店家接口、店家访问玩家接口
3. 剧本库：查询系统剧本列表
4. 玩家局主链路：
   - 创建玩家局
   - 查询详情和列表
   - 发布人创建并支付押金
   - 玩家加入、创建并支付押金
   - 满员后发起 `complete`
   - 全员 `confirm` 后进入 `COMPLETED`
   - 创建并支付尾款
   - 发起 `finish`
   - 全员 `confirm` 后进入 `FINISHED`
   - 完成后评价 DM
5. 审核制玩家局：
   - 创建审核制拼车
   - 玩家申请加入
   - 审核前禁止创建押金订单
   - 非发布人不能审核
   - 发布人通过申请后允许创建押金订单
6. 店家基础链路：
   - 店家创建店铺
   - 店员加入店铺
   - 店长设置店员为管理员
   - 店长查看成员列表
   - 店长添加 `scriptId=1` 到店铺剧本库
   - 店长发布店家局
   - 店长指派店铺成员为 DM
7. 消息和群聊基础异常：
   - 查询消息列表
   - 非成员或已完成场景发送空群聊消息被拒绝

#### 3. 覆盖的异常情况

本轮验证通过的异常场景包括：

- 重复手机号注册失败
- 错误密码登录失败
- 无 Token 访问受保护接口失败
- 角色路径不匹配访问失败
- `OPEN` 状态未满员时直接发起 `complete` 失败
- 非成员创建押金订单失败
- 他人支付订单失败
- 重复支付订单失败
- 重复创建押金订单失败
- 重复加入拼车失败
- 满员后继续加入失败
- 非发布人发起 `complete` 失败
- 非正式成员提交确认失败
- 重复确认失败
- `COMPLETED` 后转让 DM 失败
- `COMPLETED` 后继续加入失败
- `FINISHED` 后再次 `finish` 失败
- 重复评价失败
- 非法评分失败
- 审核前创建订单失败
- 非发布人审核失败
- 普通店员设置权限失败
- 玩家调用店铺接口失败
- 指派非店铺成员为 DM 失败

#### 4. 本轮发现并修复的问题

**问题 1：`scriptId=1` 创建拼车时报 500**

- 原因：当前数据库 `script` 表为空，但 `car_pool.script_id` 存在外键约束，传入不存在的 `scriptId=1` 导致数据库外键异常。
- 修复：
  - 初始化 SQL 显式插入 `id=1` 的默认测试剧本
  - `PoolServiceImpl.create()` 中增加 `scriptId` 存在且未下架校验
  - 店家局增加“剧本必须在店铺剧本库中”的业务校验
- 验证：带 `scriptId=1` 创建玩家局和店家局均成功；不存在的 `scriptId` 返回“剧本不存在或已下架”。

**问题 2：非成员可以创建押金订单**

- 原因：`OrderServiceImpl.create()` 只校验拼车存在和重复订单，没有校验 `pool_member`。
- 修复：
  - 创建订单前查询 `pool_member`
  - 非成员禁止创建订单
  - 押金订单仅允许 `PENDING_PAYMENT` 成员创建
  - 车费订单仅允许 `JOINED` 成员且拼车状态为 `COMPLETED` 时创建
- 验证：非成员创建押金订单失败；审核前创建订单失败；审核通过后创建订单成功。

**问题 3：用户可以支付他人订单**

- 原因：`OrderService.pay(orderNo)` 未接收当前登录用户 ID，也未校验订单归属。
- 修复：
  - `OrderService.pay(String orderNo)` 改为 `pay(Long userId, String orderNo)`
  - `PlayerOrderController` 调用时传入 `BaseContext.getCurrentId()`
  - `OrderServiceImpl.pay()` 校验 `order.userId == currentUserId`
- 验证：玩家2支付发布人的订单失败，返回“无权限操作他人订单”；发布人支付自己的订单成功。

**问题 4：硬编码常量分散**

- 新增常量类：
  - `ErrorConstant`
  - `DbFieldConstant`
  - `RedisKeyConstant`
  - `JwtConstant`
  - `ApiPathConstant`
- 已替换关键路径中的硬编码：
  - JWT claim/header/Bearer 前缀
  - Redis key 前缀
  - 鉴权路径前缀
  - 订单/拼车/用户服务中的部分数据库字段名和错误文案
- 说明：仍有部分模块如 `ShopServiceImpl`、`ScriptServiceImpl`、`ReviewServiceImpl`、`MessageServiceImpl` 存在硬编码，后续可按模块继续收敛。

#### 5. 当前项目状态

当前后端主链路已达到可联调状态：

- 玩家局核心流程已跑通
- 审核制加入与订单创建约束已跑通
- 店铺、店员、店铺剧本、店家局发布和 DM 指派已跑通
- 支付订单归属校验已生效
- 本轮命令行接口测试 `69/69` 通过

#### 6. 下一步建议

当前建议进入前端开发前，再做一轮小范围后端补强：

1. 统一异常 HTTP 状态码，目前大量业务错误仍是 `HTTP 200 + code=500`
2. 补充评价接口的成员资格校验，避免非成员评价
3. 补充取消、退出/跳车、退款、超时任务等异常链路测试
4. 继续按模块收敛硬编码常量

完成上述补强后，再启动 Vue 3 + Vant 前端项目会更稳。

---

## 日期：2026-05-22（第二段）

### 本轮操作：后端补强继续推进

#### 1. 订单创建与支付安全复测

在重启后端后，重新使用命令行脚本模拟 Apifox 执行完整接口链路测试：

- 报告文件：`/tmp/jupin-api-test-v2-1779450471/report.md`
- 测试结果：`PASS 69 / FAIL 0`

本轮确认以下问题已修复：

- 非成员创建押金订单被拦截
- 审核制拼车中，待审核成员创建押金订单被拦截
- 审核通过后创建押金订单成功
- 他人支付订单被拦截，返回“无权限操作他人订单”
- 发布人支付自己的订单成功
- 店家局指派 DM 正常

#### 2. 评价成员资格校验修复与定向测试

修改 `ReviewServiceImpl.create()`，补充评价资格校验：

- 拼车必须存在
- 拼车状态必须为 `FINISHED`
- 评价类型只能为 `0-店铺` 或 `1-DM`
- 评价人必须是该拼车的正式成员：`pool_member.status = JOINED`
- 评价 DM 时，`targetId` 必须等于当前拼车 `dmId`
- 评价店铺时，必须是店家局，且 `targetId` 必须等于当前拼车 `shopId`
- 保留重复评价拦截

定向测试复用已完成的 `poolId=19`，没有重新跑完整拼车流程：

| 用例 | 结果 |
|------|------|
| 非成员评价 | PASS，返回“只有正式参与成员才能评价” |
| 成员评价错误 DM target | PASS，返回“评价对象不属于该拼车” |
| 玩家局评价店铺 | PASS，返回“评价对象不属于该拼车” |
| 已评价用户重复评价 | PASS，返回“你已经评价过” |

#### 3. 常量类与硬编码收敛

新增并开始使用以下常量类：

- `ErrorConstant`
- `DbFieldConstant`
- `RedisKeyConstant`
- `JwtConstant`
- `ApiPathConstant`

已替换的重点范围：

- JWT claim/header/Bearer 前缀
- Redis key 前缀
- API 路径前缀
- 用户、订单、拼车、评价服务中的部分错误文案和数据库字段名

修正过程中曾发现 `PoolServiceImpl` 中残留 `LOCK_KEY_PREFIX`、`ROLE_KEY_PREFIX` 未定义引用，已统一替换为：

- `RedisKeyConstant.POOL_LOCK_PREFIX`
- `RedisKeyConstant.POOL_ROLE_PREFIX`

#### 4. 取消与退出链路补强（已编码，待重启验证）

本轮继续补强 `cancel` 和 `leave`：

**取消拼车：**

- 在玩家端新增接口：`POST /api/player/pool/{poolId}/cancel`
- 在店家端新增接口：`POST /api/shop/pool/{poolId}/cancel`
- 复用 `PoolService.cancel(userId, poolId)`
- 取消成功后，自动将该拼车已支付订单更新为已退款：
  - `order.status = REFUNDED`
  - `refund_time = now`
  - `refund_reason = 拼车取消自动退款`

**退出/跳车：**

- 修正 `PoolServiceImpl.leave()` 的人数扣减逻辑
- 原问题：待审核/待支付成员退出时也会让 `currentMembers - 1`
- 新逻辑：仅当成员原状态为 `JOINED` 时才扣减 `currentMembers`
- `FULL` 状态下已加入成员退出后，自动回退到 `OPEN`
- `COMPLETED` 后退出仍标记为 `LEFT`，作为后续跳车扣信用/群聊移除的基础

#### 5. 当前待验证事项

当前最新代码已修改，但还需要重新编译运行后验证：

1. 玩家局 `OPEN` 状态取消成功
2. 非发布人取消失败
3. `COMPLETED` 后取消失败
4. 取消后已支付订单自动退款
5. 待支付成员退出不减少 `currentMembers`
6. 已加入成员退出会减少 `currentMembers`
7. `FULL` 状态成员退出后回退为 `OPEN`

#### 6. 下一步建议

下一步优先级：

1. 重启后端并执行取消/退出定向测试
2. 继续补跳车信用分扣减和群聊权限移除
3. 再处理统一异常 HTTP 状态码
4. 最后进入前端开发

---

## 日期：2026-05-22（第三段）

### 本轮操作：取消/退出定向测试与修复

#### 1. 定向测试脚本

脚本路径：`/tmp/jupin_cancel_leave_test.sh`
测试方式：注册独立账号，创建 3 个拼车覆盖 7 个场景

**测试场景与结果：**

| # | 场景 | 预期 | 实际 |
|---|------|------|------|
| 1 | OPEN 状态发布人取消 | 成功 | PASS |
| 2 | 非发布人取消 | 失败 | PASS |
| 3 | COMPLETED 后取消 | 失败 | PASS |
| 4 | 取消后订单 refunded + refund_time + refund_reason | 正确填写 | PASS |
| 5 | 待支付(PENDING_PAYMENT)成员退出 | currentMembers 不变 | PASS |
| 6 | 已加入(JOINED)成员退出 | currentMembers-1 | PASS |
| 7 | FULL 已加入成员退出 | 回退 OPEN | PASS |

最终结果：**PASS 35 / FAIL 0**

测试报告：`/tmp/jupin-cancel-test-1779455849/report.md`

#### 2. 发现并修复的问题

**问题 1：LEFT 成员重新加入拼车报 500**

- 原因：`PoolServiceImpl.join()` 中 LEFT 或 REJECTED 成员重新加入时，重复检测只排除了活跃状态(JOINED/PENDING_PAYMENT/PENDING_REVIEW)，但 INSERT 会因 `uk_pool_user` 唯一约束失败。
- 修复：先查询现有记录，若为 LEFT/REJECTED 则 UPDATE 回待支付/待审核状态，不再 INSERT。
- 涉及文件：`PoolServiceImpl.java:join()`

**问题 2：OrderVO 缺少 refundReason 字段**

- 原因：`OrderVO` 未定义 `refundReason`，导致 `BeanUtil.copyProperties()` 跳过退款原因，API 响应中永远为空。
- 修复：OrderVO 新增 `refundReason` 字段。
- 涉及文件：`OrderVO.java`

#### 3. 本次修改汇总

| 文件 | 修改内容 |
|------|---------|
| `jupin-server/.../PoolServiceImpl.java` | join() 中 LEFT/REJECTED 成员重入改为 UPDATE |
| `jupin-pojo/.../OrderVO.java` | 新增 refundReason 字段 |

---

## 日期：2026-05-22（第四段）

### 本轮操作：信用分扣减 + 群聊权限 + confirm 修复 + 定向测试

#### 1. 信用分跳车扣减（Task 1）

**需求：** 拼车 `COMPLETED` 后，成员跳车需根据距离开团时间梯度扣减信用分。

**实现：**
- `PoolServiceImpl.leave()` 中新增 `COMPLETED` 分支
- 调用 `creditService.deduct()` 扣减信用分并写入 `credit_log` 表
- 扣减梯度：>24h = -10, 2~24h = -20, <2h = -30
- 7天内多次跳车额外 -5
- 新增 `calculateLeavePenalty()` 和 `buildLeavePenaltyReason()` 私有方法

#### 2. 群聊权限移除（Task 2）

**问题：** `ChatServiceImpl.sendMessage()` 用硬编码 `status = 1`（实际是 PENDING_PAYMENT）判断成员资格，导致 LEFT 成员仍可发送消息。

**修复：**
- 改为 `MemberStatus.JOINED` 常量（值为 2）
- 新增空消息校验 `if (content == null || content.trim().isEmpty())`
- 涉及文件：`ChatServiceImpl.java`

#### 3. DbFieldConstant 类名修正

**问题：** 类名误写为 `cDbFieldConstant`，编译报错 `java: 方法声明无效; 需要返回类型`。

**修复：** 类名改为 `DbFieldConstant`。

#### 4. confirm() 500 问题排查

**现象：** 注入 `CreditService` 后，`confirm()` 对新拼车返回 500 "服务器内部错误"。

**排查过程：**
- 在 `confirm()` 外层加 try-catch 日志包装后重新编译运行
- 实际发现 `confirm()` 本身无问题（测试步骤 11、12 均 PASS）
- 真正炸的是后续 `leave()` 方法

**修复：** 无需要修复 confirm 本身，实际为 credit_log 表 SQL 问题在下文修复。

#### 5. MySQL 保留关键字 `change` 导致 SQL 异常

**问题：** `credit_log` 表字段 `change` 是 MySQL 保留关键字，MyBatis-Plus 生成 SQL 时未加反引号：
```
INSERT INTO credit_log (user_id, change, balance, reason, create_time) VALUES (...)
```
导致 `leave()` 和信用分日志查询均报 `SQLSyntaxErrorException`。

**修复过程（绕弯路）：**
1. ❌ 尝试 `application.yml` 加 `global-config.db-config.column-format: '`{0}`'` — 全局加反引号导致 MyBatis-Plus 初始化失败，全部接口 500
2. ✅ 回退配置，改为 `@TableField("`change`")` 仅在 CreditLog 实体 `change` 字段加注解

**涉及文件：**
- `jupin-pojo/.../CreditLog.java` — 新增 `@TableField("`change`")`

#### 6. 错误日志包装

为方便今后排查，对以下方法增加了 try-catch 日志包装，将真实异常消息暴露在 API 响应中：

- `PoolServiceImpl.confirm()` → `doConfirm()` 内部方法
- `PoolServiceImpl.leave()` → `doLeave()` 内部方法
- `CreditServiceImpl.getLog()` — 新增 `@Slf4j` 注解

#### 7. 信用分扣减定向测试

**测试脚本：** `/tmp/jupin_credit_test.sh`

测试流程：
1. 注册两个玩家账号
2. 发布人创建拼车、支付押金
3. 玩家2加入、支付押金
4. 发布人发起 complete，两人 confirm 进入 COMPLETED
5. 玩家2跳车
6. 验证信用分扣减和日志

**最终结果：PASS 16 / FAIL 0**

| # | 用例 | 结果 |
|---|------|------|
| 1 | 注册发布人 | PASS |
| 2 | 查初始信用分（100） | PASS |
| 3 | 注册玩家2 | PASS |
| 4 | 创建拼车 | PASS |
| 5 | 发布人押金 | PASS |
| 6 | 发布人支付 | PASS |
| 7 | 玩家2加入 | PASS |
| 8 | 玩家2押金 | PASS |
| 9 | 玩家2支付 | PASS |
| 10 | 发布人 complete | PASS |
| 11 | 发布人 confirm | PASS |
| 12 | 玩家2 confirm（进入 COMPLETED） | PASS |
| 13 | 玩家2 跳车（COMPLETED 后） | PASS |
| 14 | 查跳车后信用分（100→90） | PASS |
| 15 | 查信用分日志（原因：距开团超过24小时跳车） | PASS |
| 16 | 验证扣减（before=100, after=90, change=-10, reason=距开团超过24小时跳车） | PASS |

#### 8. 本次修改汇总

| 文件 | 修改内容 |
|------|---------|
| `jupin-server/.../PoolServiceImpl.java` | leave() 新增 COMPLETED 信用分扣减；confirm()/leave() 加 error logging |
| `jupin-server/.../ChatServiceImpl.java` | 群聊成员资格改为 `MemberStatus.JOINED`；加空消息校验 |
| `jupin-common/.../DbFieldConstant.java` | 类名 `cDbFieldConstant` → `DbFieldConstant` |
| `jupin-pojo/.../CreditLog.java` | `change` 字段加 `@TableField("`change`")` 转义 MySQL 关键字 |
| `jupin-server/.../CreditServiceImpl.java` | getLog() 加 try-catch + @Slf4j |
| `jupin-server/.../application.yml` | 回退全局 column-format |

---

## 日期：2026-05-22（第五段）

### 本轮操作：git 提交、项目收尾

#### 1. Git 提交

将累计修改按语义拆分为 12 个 commit：

```
feat: 跳车扣信用分，LEFT重入改为UPDATE
feat: 订单创建校验成员资格，pay()增userId归属校验
feat: 评价接口增加成员资格和评价对象校验
feat: 玩家/店家端新增取消拼车接口
fix: @TableField转义MySQL关键字change
fix: 群聊成员资格改为MemberStatus.JOINED
fix: getLog加错误日志包装
fix: OrderVO新增refundReason字段
refactor: 硬编码提取为常量类
chore: 新增常量类
docs: 更新开发日志
chore: 更新初始化SQL
```

#### 2. 当前后端完成状态

| 模块 | 状态 |
|------|------|
| 公共认证（注册/登录/刷新/退出） | ✅ 已验证 |
| 权限隔离（玩家/店家/管理员路由拦截） | ✅ 已验证 |
| 玩家局主链路（创建→满员→complete→confirm→COMPLETED→finish→FINISHED） | ✅ 已验证 |
| 审核制玩家局 | ✅ 已验证 |
| 店家局（店铺/店员/权限/剧本库/发布/指派DM） | ✅ 已验证 |
| 订单安全（成员资格校验、归属校验） | ✅ 已验证 |
| 评价校验（成员资格、评价对象、重复评价） | ✅ 已验证 |
| 取消拼车 + 自动退款 | ✅ 已验证 |
| 退出/跳车（含人数扣减、FULL回退OPEN） | ✅ 已验证 |
| 群聊（成员资格、空消息拦截） | ✅ 已验证 |
| 消息通知 | ✅ 已验证 |
| 信用分跳车扣减 | ✅ 定向测试 16/0 |
| 常量抽取（5 个常量类） | ✅ 已完成 |

#### 3. 下一步

**明天直接开始前端构建（Vue 3 + Vant UI）。**

---

## 日期：2026-05-23（第一段）

### 本轮操作：Docker 编译与运行链路修复

#### 1. 问题现象

此前项目只能在本地直接编译运行，无法稳定通过 Docker 环境完成后端镜像构建与容器启动。

排查到的问题包括：
- Dockerfile 固定使用 `--platform=linux/amd64`，在当前 Colima / Apple Silicon 环境下会触发平台不匹配警告。
- `eclipse-temurin:17-jre-alpine` 在当前架构下拉取失败，提示 `no match for platform in manifest`。
- `mvn dependency:go-offline` 会拉取大量无关 Maven 插件和报告依赖，首次构建时间过长。
- App 容器内连接 Redis 时仍使用 `6380`，但 `6380` 是宿主机映射端口；容器网络内应访问 `redis:6379`。
- 本地 Java 后端占用 `8080`，Docker app 如直接映射 `8080:8080` 会与本地服务冲突。

#### 2. 修复内容

| 文件 | 修改内容 |
|------|---------|
| `Dockerfile` | 去掉固定 `linux/amd64`；运行镜像改为 `eclipse-temurin:17-jre-jammy`；去掉 `mvn dependency:go-offline`；使用 BuildKit Maven 缓存；构建命令改为 `mvn -pl jupin-server -am package -Dmaven.test.skip=true -B` |
| `docker-compose.yml` | 删除过期的 `version` 字段；给 app 环境变量新增 `SPRING_REDIS_PORT: 6379` |

#### 3. 验证结果

执行 Docker 构建：

```bash
docker compose build app
```

结果：
- 首次构建成功，耗时约 4 分钟。
- Maven 依赖缓存建立后，二次构建成功，耗时约 3 秒。

由于本地 Java 后端已占用 `8080`，使用临时端口 `8081` 启动 Docker app：

```bash
APP_PORT=8081 docker compose up -d app
```

结果：
- `jp-mysql`、`jp-redis`、`jp-rabbitmq` 均为 healthy。
- `jp-app` 状态为 `Up`，端口映射为 `0.0.0.0:8081->8080/tcp`。
- `curl --noproxy '*' http://127.0.0.1:8081/v3/api-docs` 返回 `HTTP/1.1 200`。

#### 4. 后续运行约定

---

## 日期：2026-05-24

### 本轮操作：前端"我的"页面修复 — 待办事项显示关联拼车、我的拼车拆分、偏好排序说明

#### 1. 修复待办事项不显示关联拼车信息

**问题：** 待办列表中基于订单（orders）的待支付项只显示 `订单 ¥{amount}`，没有显示关联的是哪个拼车。

**修复：**
- 新增 `poolNameMap` 计算属性，从 `memberships` 数据构建 `poolId → scriptName` 映射
- `buildTodos()` 中订单待办项的 `desc` 改为 `${poolNameMap[o.poolId] ? `《${poolNameMap[o.poolId]}》` : ''}订单 ¥${formatPrice(o.amount)}`
- 新增 `watch([orders, memberships], () => buildTodos())`，确保待办列表在数据变化后自动刷新

#### 2. 修复"我的拼车"不显示加入/退出的拼车

**问题：** 原模板只展示了发布人自己发布的拼车（`myPools`），没有展示用户作为成员加入或退出的拼车。

**修复：**
- 将 `myPools` 重命名为 `allPools`（存储所有拼车列表）
- 新增 `ownedPools` 计算属性（`allPools` 按 `ownerId` 过滤）
- 新增 `joinedPools` 计算属性（直接映射 `memberships`）
- 模板拆分为"我发布的"（使用 `PoolCard` 组件）和"我参与的"（使用精简卡片，显示成员状态标签 + 拼车状态标签 `StatusTag`）
- 成员状态标签：待审核(0)/待支付(1)/已加入(2)/已退出(3)
- 拼车状态标签：仅在已加入/已退出时显示

#### 3. 偏好排序说明

**问题：** 玩家局列表页面的 `recommend` 参数在 `PoolServiceImpl.list()` 中接收但从未使用，排序始终是 `CREATE_TIME DESC`。

**结论：** 这是后端功能缺失，`recommend` 参数形同虚设，需要后端实现偏好匹配和排序逻辑才能真正生效。前端无法独立修复。

#### 4. 修改文件清单

| 文件 | 修改内容 |
|------|---------|
| `jupin-web/src/views/player/MyPage.vue` | 新增 `poolNameMap` 计算属性、`watch` 自动刷新、`ownedPools`/`joinedPools` 计算属性、模板拆分、CSS 样式、导入 `watch`/`StatusTag` |

#### 5. 当前前端待处理问题

- 偏好排序（后端 `recommend` 未实现）
- 加入拼车后未支付押金的待办项仍可能缺失（#5）
- 缺少 DM 评价展示入口（#14）
- 账号系统角色前置选择（#15）
- 店家端缺少"我的"页面（#19）

如果完全使用 Docker 跑后端，先停止本地 Java 后端，然后直接运行：

```bash
docker compose up -d app
```

如果需要保留本地后端，同时启动 Docker 后端用于对照验证，则使用：

```bash
APP_PORT=8081 docker compose up -d app
```

注意：
- 本地开发配置中 Redis 访问宿主机端口 `localhost:6380`。
- Docker 容器内部访问 Redis 必须使用服务名和容器端口 `redis:6379`。
- `localhost` 可能被系统代理影响，接口验证优先使用 `127.0.0.1` 并加 `--noproxy '*'`。

---

## 日期：2026-05-24（第二段）

### 本轮操作：后端编译与 Docker 构建修复

#### 1. 问题现象

当前项目突然无法正常编译运行。前端执行 `npm run build` 可以通过，问题集中在后端 Java 编译和 Docker 构建链路。

本地环境中没有可用的 `mvn` 命令，也没有 Maven Wrapper（`./mvnw`），因此后端编译主要通过 Docker 镜像构建进行验证。

#### 2. 根因分析

| 问题位置 | 根因 |
|---------|------|
| `MessageServiceImpl.getList()` | 分页变量从 `p` 改为 `pageResult` 后，返回处仍使用旧变量 `p.getRecords()`，导致 Java 编译失败 |
| `PoolServiceImpl.create()` | 引用了不存在的 `ErrorConstant.SHOP_SCRIPT_NOT_IN_LIBRARY`，实际已有常量是 `ErrorConstant.SCRIPT_NOT_IN_LIBRARY` |
| `PoolServiceImpl.doConfirm()` | 方法内已有局部变量 `member`，stream lambda 又使用 `member -> ...`，Java 不允许在重叠作用域重复声明同名变量 |
| `Dockerfile` | 使用 `RUN --mount=type=cache,target=/root/.m2`，该语法依赖 BuildKit/buildx；当前本地 Docker 缺少可用 buildx 支持，导致构建失败 |

#### 3. 修复内容

| 文件 | 修改内容 |
|------|---------|
| `jupin/jupin-server/src/main/java/com/jupin/server/service/impl/MessageServiceImpl.java` | 将返回值修正为 `pageResult.getRecords()` |
| `jupin/jupin-server/src/main/java/com/jupin/server/service/impl/PoolServiceImpl.java` | 将错误常量改为 `ErrorConstant.SCRIPT_NOT_IN_LIBRARY`；将 lambda 参数从 `member` 改为 `poolMember`，避免变量名冲突 |
| `jupin/Dockerfile` | 去除 BuildKit 专用 `RUN --mount` 语法，改为普通 `RUN mvn -pl jupin-server -am package -Dmaven.test.skip=true -B` |

#### 4. 验证结果

前端构建：

```bash
cd jupin-web
npm run build
```

结果：构建通过，仅有 Vite/Rolldown 体积和注释相关 warning，不影响产物生成。

后端 Docker 编译验证：

```bash
cd jupin
docker build --target builder -t jupin-backend-compile-check .
```

结果：
- `jupin-common` 编译成功
- `jupin-pojo` 编译成功
- `jupin-server` 编译成功
- Maven 输出 `BUILD SUCCESS`
- Docker 镜像构建成功，生成 `jupin-backend-compile-check:latest`

#### 5. 后续注意

- 当前 Dockerfile 不再依赖 BuildKit，因此兼容性更好。
- 代价是首次 Docker 构建会重新下载 Maven 依赖，速度较慢。本次首次完整验证耗时约 21 分钟。
- 如果后续开发环境稳定支持 buildx/BuildKit，可以再考虑恢复 Maven cache mount，提高 Docker 构建速度。
