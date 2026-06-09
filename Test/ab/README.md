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

**场景**：300 次并发读取拼车详情，分三条子路径：

| 子场景 | ID 分布 | 测试目标 |
|---|---|---|
| Happy | 100% 存在的 ID | 缓存命中延迟 |
| Mixed | 50% 存在 / 50% 不存在 | 混合负载 |
| Penetration | 100% 不存在（10 个固定 ID 循环） | 穿透防护有效性 |

**测量指标**：
- 各子场景的 P50/P95/P99 延迟
- Penetration 场景下首次请求 vs 后续请求的延迟差异
- 估算 DB 命中次数

**A vs B**：
- **A（无缓存）**：每次请求都查 MySQL，P95 较高；穿透场景每个不存在的 ID 都触发 DB 查询
- **B（有缓存）**：热点详情命中 Redis，P95 显著降低；不存在 ID 通过短 TTL 空值缓存拦截重复穿透；固定 TTL + 随机偏移降低热点 Key 同时过期风险；写操作后主动删除详情缓存保证数据新鲜度

## 执行协议

### 运行方式

```bash
# 运行全部三个测试
bash Test/ab/ab_suite.sh

# 仅运行指定测试
TESTS=oversell,cache bash Test/ab/ab_suite.sh

# 自定义参数
PLAYER_COUNT=30 POOL_MAX_MEMBERS=5 AB_ROUNDS=10 bash Test/ab/ab_suite.sh
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

### 结果输出

结果保存在 `Test/ab_results/` 下，按 `{RUN_ID}_{test_name}` 组织：

```
Test/ab_results/
└── 20260606120000_oversell/
    ├── 20260606120000_oversell_A/    # A 变体各轮原始 + 汇总 TSV
    ├── 20260606120000_oversell_B/    # B 变体各轮原始 + 汇总 TSV
    └── 20260606120000_oversell_comparison.tsv  # A/B 对比报告
```

对比报告格式 (`_comparison.tsv`):

```
metric          A_mean    A_stddev    B_mean    B_stddev    improvement_pct
p95_seconds     0.1234    0.0100      0.0890    0.0080      27.9
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
