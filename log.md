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
