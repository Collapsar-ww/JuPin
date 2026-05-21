# 项目日志

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
