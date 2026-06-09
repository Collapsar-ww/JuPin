# JuPin 并发安全 A/B 压测框架

## 概述

本框架通过 **Git 分支切换 + 单变量控制** 的方式，对比三个并发安全优化点的"有防护"与"无防护"表现，产出可写入简历的量化数据。

核心思路：在 `cleanup-bench-review`（全量优化分支）之上，创建三个临时基线分支，每个分支 **仅移除一个防护机制**，其余代码与优化分支完全一致。对比测试通过 shell 脚本编排 git checkout → 编译重启 → 压测 → 聚合报告的流程。

## 分支结构

```
cleanup-bench-review (HEAD, 全量优化)
├── ab-baseline-oversell    — 仅移除【超员防护】
├── ab-baseline-idempotent  — 仅移除【订单幂等】
└── ab-baseline-cache       — 仅移除【拼车缓存】
```

### 各基线分支变更详情

| 基线分支 | 修改文件 | 移除内容 |
|---|---|---|
| `ab-baseline-oversell` | `OrderServiceImpl.java` | Redisson 分布式锁 (`RLock.tryLock`)、容量守卫 (`current_members < max_members`)、成员状态守卫 (`PENDING_PAYMENT`)、人数反算 |
| `ab-baseline-idempotent` | `OrderServiceImpl.java` | 下单幂等 Key 预查、`DuplicateKeyException` 捕获并回查、支付事件去重异常捕获 |
| `ab-baseline-cache` | `PoolServiceImpl.java` | Cache Aside 读缓存、空值缓存哨兵、固定 TTL + 随机偏移写缓存、写后主动失效 |

三个基线分支各自只移除一个优化点（oversell: `payDeposit()`, idempotent: `create()` + `insertPaymentEvent()`, cache: `getDetail()` 及相关写后失效调用），改动范围需要保持精确可控。

## 测试脚本

```
Test/ab/
├── ab_common.sh       # 公共库：分支切换、后端就绪检测、统计聚合
├── ab_oversell.sh     # 超员防护 A/B 测试
├── ab_idempotent.sh   # 订单幂等 A/B 测试
├── ab_cache.sh        # 拼车缓存 A/B 测试
├── ab_pressure_suite.sh # 梯度压力测试入口
└── ab_suite.sh        # 总调度入口
```

### 分项测试说明

#### 1. 超员防护 (`ab_oversell.sh`)

**场景**：N 个玩家（默认 20）并发加入一个限员 M（默认 3）的拼车局，加入后立即创建订单并支付。

**测量指标**：
- 成功支付人数 vs 车局上限（超员率）
- `current_members` 与实际 JOINED 成员数的漂移
- 重复成员检测
- P95 延迟

**A vs B**：
- **A（无防护）**：无锁并发 increment，预期超员率 > 40%，`current_members` 漂移
- **B（有防护）**：分布式锁串行化 + 条件更新，预期超员率 0%，漂移 0

#### 2. 订单幂等 (`ab_idempotent.sh`)

**子场景 1 — 并发创建**：N 个请求（默认 50）用相同的 `idempotentKey` 并发创建订单。

**子场景 2 — 并发回调**：N 个请求（默认 50）用相同的 `channelTxnId` 并发 Mock 支付回调。

**测量指标**：
- HTTP 200 数量（含业务 200）
- HTTP 500 数量
- 实际落库的订单/支付事件条数（distinct orders / events）

**A vs B**：
- **A（无防护）**：唯一索引冲突导致大量 500，仅约 2% 请求成功
- **B（有防护）**：100% 返回 200，仅创建 1 条记录

#### 3. 拼车缓存 (`ab_cache.sh`)

**场景**：并发读取拼车详情，分五条子路径，既测热点缓存性能，也测缓存穿透防护。

| 子场景 | ID 分布 | 测试目标 |
|---|---|---|
| Happy | 100% 存在的 ID | 缓存命中延迟 |
| Mixed | 50% 存在 / 50% 重复不存在 | 混合读负载 |
| Penetration Repeat Cold | 100% 不存在，10 个固定 ID 循环，冷启动 | 验证首轮穿透写入空值缓存 |
| Penetration Repeat Warm | 同一批不存在 ID 再打一轮 | 验证短 TTL 空值缓存是否拦截重复穿透 |
| Penetration Unique | 100% 不存在，每个请求不同 ID | 验证随机不存在 ID 对 DB 的真实压力 |

**测量指标**：
- 各子场景的 P50/P95/P99 延迟
- Repeat Cold vs Repeat Warm 的 P95 差异
- Redis 中 `pool:detail:{id}` 空值缓存 key 数量
- Warm 轮中可命中空值缓存的请求数
- Unique 不存在 ID 场景的 P95，用于说明空值缓存只能拦截重复穿透，不能消除随机 ID 穿透
- 当前脚本不直接统计 Java 层实际 MySQL 查询次数；如需精确 DB 查询数，需要额外接入应用埋点或 MySQL general log

**A vs B**：
- **A（无缓存）**：每次请求都查 MySQL，P95 较高；穿透场景每个不存在的 ID 都触发 DB 查询
- **B（有缓存）**：热点详情命中 Redis，P95 显著降低；不存在 ID 通过短 TTL 空值缓存拦截重复穿透；固定 TTL + 随机偏移降低热点 Key 同时过期风险；写操作后主动删除详情缓存保证数据新鲜度

## 执行协议

### 运行方式

#### 冒烟测试

冒烟测试只用于确认脚本、分支切换、数据库重置和核心断言是否正常，不作为压力测试结论。

```bash
TESTS=oversell AB_ROUNDS=1 AB_WARMUP=0 AB_RESET_EACH_ROUND=true bash Test/ab/ab_suite.sh
TESTS=idempotent AB_ROUNDS=1 AB_WARMUP=0 AB_RESET_EACH_ROUND=true bash Test/ab/ab_suite.sh
TESTS=cache AB_ROUNDS=1 AB_WARMUP=0 AB_RESET_EACH_ROUND=true bash Test/ab/ab_suite.sh
```

#### 单档 A/B 基准

`ab_suite.sh` 每次只使用一组参数，适合固定某个压力点做重复采样。

```bash
# 运行全部三个测试
bash Test/ab/ab_suite.sh

# 仅运行指定测试
TESTS=oversell,cache bash Test/ab/ab_suite.sh

# 自定义参数
PLAYER_COUNT=30 POOL_MAX_MEMBERS=5 AB_ROUNDS=10 bash Test/ab/ab_suite.sh
```

#### 梯度压力测试

正式压力测试使用 `ab_pressure_suite.sh`。该脚本会按 L1/L2/L3/L4 梯度逐档执行，每一档内部仍然走严格 A/B：

1. 切换到对应基线分支；
2. 提示人工编译并重启后端；
3. 执行 A 组预热和正式轮次；
4. 切换回 `cleanup-bench-review`；
5. 再次提示人工编译并重启后端；
6. 执行 B 组预热和正式轮次；
7. 进入下一压力档。

默认梯度：

| 测试 | L1 | L2 | L3 | L4 |
|---|---|---|---|---|
| oversell | 20 人 / 并发 10 / 3 座 | 50 人 / 并发 25 / 3 座 | 100 人 / 并发 50 / 3 座 | 200 人 / 并发 100 / 3 座 |
| idempotent | 50 请求 / 并发 10 | 100 请求 / 并发 25 | 200 请求 / 并发 50 | 500 请求 / 并发 100 |
| cache | 300 请求 / 并发 30 | 1000 请求 / 并发 100 | 3000 请求 / 并发 200 | 10000 请求 / 并发 300 |

执行全部梯度：

```bash
AB_ROUNDS=3 AB_WARMUP=1 AB_RESET_EACH_ROUND=true bash Test/ab/ab_pressure_suite.sh
```

仅执行某一项梯度：

```bash
TESTS=oversell AB_ROUNDS=3 AB_WARMUP=1 bash Test/ab/ab_pressure_suite.sh
TESTS=idempotent AB_ROUNDS=3 AB_WARMUP=1 bash Test/ab/ab_pressure_suite.sh
TESTS=cache AB_ROUNDS=3 AB_WARMUP=1 bash Test/ab/ab_pressure_suite.sh
```

自定义梯度：

```bash
OVERSELL_LEVELS=50:25:3,100:50:3,300:150:3 TESTS=oversell bash Test/ab/ab_pressure_suite.sh
IDEMPOTENT_LEVELS=100:25,300:75,1000:200 TESTS=idempotent bash Test/ab/ab_pressure_suite.sh
CACHE_LEVELS=1000:100:1,5000:300:1,20000:500:1 TESTS=cache bash Test/ab/ab_pressure_suite.sh
```

### 协议细节

1. **预热**：每个变体 2 轮预热（warmup），数据丢弃，消除 JIT/连接池冷启动影响
2. **数据采集**：每个变体 5 轮正式数据（`AB_ROUNDS`），A 先全部跑完再跑 B（避免频繁切换分支）
3. **每轮重置状态**：默认每个 A/B 轮次开始前自动执行 `Test/ab/reset_state.sh`，重建 MySQL 数据并清空 Redis，避免用户、车局、订单、幂等 Key 和缓存跨轮污染
4. **每轮自包含**：重置后注册新临时玩家 + 创建新车局，脚本内写用户、车局和订单上下文也会在每轮前清空
5. **统计输出**：均值、P50、P95、P99、标准差，跨轮取均值

### 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | 后端地址 |
| `RUN_ID` | `YYYYMMDDHHmmss` | 本次运行标识 |
| `AB_ROUNDS` | `5` | 每变体数据采集轮数 |
| `AB_WARMUP` | `2` | 每变体预热轮数 |
| `AB_RESET_EACH_ROUND` | `true` | 每个 A/B 轮次前是否自动重建数据库并清空 Redis |
| `PLAYER_COUNT` | `20` | 超员测试玩家数 |
| `POOL_MAX_MEMBERS` | `3` | 超员测试车局上限 |
| `TESTS` | `oversell,idempotent,cache` | 逗号分隔的测试列表 |
| `OVERSELL_LEVELS` | `20:10:3,50:25:3,100:50:3,200:100:3` | 梯度压测超员档位，格式为 `玩家数:并发数:座位数` |
| `IDEMPOTENT_LEVELS` | `50:10,100:25,200:50,500:100` | 梯度压测幂等档位，格式为 `请求数:并发数` |
| `CACHE_LEVELS` | `300:30:1,1000:100:1,3000:200:1,10000:300:1` | 梯度压测缓存档位，格式为 `请求数:并发数:poolId` |

### 结果输出

结果保存在 `Test/ab_results/` 下，按 `{RUN_ID}_{test_name}` 组织：

```
Test/ab_results/
└── 20260606120000_oversell/
    ├── 20260606120000_oversell_A/    # A 变体各轮原始 + 汇总 TSV
    ├── 20260606120000_oversell_B/    # B 变体各轮原始 + 汇总 TSV
    └── 20260606120000_oversell_comparison.tsv  # A/B 对比报告
```

对比报告格式 (`_comparison.tsv`)。多子场景测试会按 `scenario` 分组输出，不再把所有 P95 混成一个均值：

```
metric          A_mean    A_stddev    B_mean    B_stddev    improvement_pct
cache_happy.p95_seconds                       0.1234    0.0100    0.0890    0.0080    27.9
cache_penetration_repeat_warm.p95_seconds     0.1200    0.0100    0.0300    0.0040    75.0
```

## 预期简历数据

### 超员防护
> 30 人并发支付 3 座车局：无防护时超员率 40%+，人数漂移 N；引入 Redisson 分布式锁 + MySQL 条件更新后超员率 0%，漂移 0，P95 延迟仅增加 Zms。

### 订单幂等
> 50 并发同一幂等 Key 创建：无防护时仅 2% 请求成功，其余 500；引入幂等 Key + 唯一索引 + DuplicateKeyException 回查后 100% 成功，仅创建 1 条订单。50 并发同一 channelTxnId 回调同理。

### 拼车缓存
> 300 并发读拼车详情：Cache Aside + 空值缓存 + 固定 TTL 随机偏移 + 写后主动失效使 P95 降低 X%；穿透防护使同一不存在 ID 仅首次查 DB，短 TTL 内后续请求命中空值缓存。

## 局限性

1. **非严格 A/B 测试**：A 和 B 在时间上串行执行（需切换分支 + 重启），无法消除时间因素（系统负载波动、MySQL buffer pool 冷热差异）。
2. **小样本量**：5 轮数据采集仅能提供参考级置信度。简历中建议表述为"压测对比"而非"统计学显著"。
3. **单机环境**：未控制 MySQL/Redis 实例隔离，结果受本地资源竞争影响。
4. **手动环节**：分支切换后需人工编译重启后端（`wait_for_backend` 提示），无法全自动化。
5. **超员测试的支付路径**：走的是 Mock 回调而非真实微信支付，不包含外部 HTTP 调用延迟。

## 前置条件

- MySQL + Redis 容器运行中
- 当前分支为 `cleanup-bench-review`（三个基线分支已基于此创建）
- `mvn clean package -DskipTests` 可正常编译
- `curl` + `jq` 可用
