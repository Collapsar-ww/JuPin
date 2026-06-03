# 严格 A/B 场景压测流程

## 目标

用同机、同数据、同参数的方式对比关键优化前后表现，避免缓存预热、脏数据、写入副作用和运行顺序影响结论。

## A/B 定义

- A 版本：优化前版本，例如读链路缓存优化前的 commit。
- B 版本：优化后版本，例如当前实现。
- 两个版本必须分别记录 commit hash，不能用“当前代码”这种模糊描述。

## 执行原则

- 每轮测试前都重建数据库并清空 Redis。
- 读链路压测只请求 `/api/player/pool/{POOL_ID}`，不创建用户、不创建车局、不支付订单。
- 每个版本先预热，再正式采样。
- 每个版本至少跑 3 轮，取中位数，避免单轮抖动误判。
- A/B 都在同一台机器、同一套 Docker 中间件、同一套测试数据下执行。

## 单场景状态重置

```bash
bash Test/ab/reset_state.sh
```

这个脚本会重新导入 `jupin/sql/init.sql` 和 `seed-data.sql`，并执行 Redis `FLUSHDB`。

## 完整场景集采样

```bash
LABEL_PREFIX=A_round1 \
BASE_URL=http://localhost:8080 \
POOL_ID=1 \
TIERS=100:10,300:30,500:50 \
bash Test/ab/run_full_suite.sh
```

B 版本只需要替换 `LABEL_PREFIX`：

```bash
LABEL_PREFIX=B_round1 \
BASE_URL=http://localhost:8080 \
POOL_ID=1 \
TIERS=100:10,300:30,500:50 \
bash Test/ab/run_full_suite.sh
```

## 结果位置

结果写入 `Test/results`：

- `${RUN_ID}_${LABEL}_${SCENARIO}_raw.tsv`：每个请求的耗时、HTTP 状态码和业务结果。
- `${RUN_ID}_${LABEL}_${SCENARIO}_summary.tsv`：本轮汇总指标。
- `${RUN_ID}_${LABEL_PREFIX}_suite_status.tsv`：整轮场景执行状态。
- `${RUN_ID}_${LABEL}_consistency.tsv`：该场景后的业务一致性 SQL 校验结果。
- `${RUN_ID}_${LABEL}_reliability.tsv`：该场景后的 MQ/支付事件可靠性 SQL 校验结果。

汇总指标包括：

- `http_200`
- `business_ok`
- `avg_seconds`
- `p50_seconds`
- `p95_seconds`
- `p99_seconds`
- `throughput_req_s`
- `status_counts`

## 验收口径

- `http_200` 必须等于 `requests`。
- 业务成功场景的 `business_ok` 必须等于 `requests`。
- 无 Token 直打场景的 `business_ok` 表示“被拦截数量”，也必须等于 `requests`。
- SQL/MQ 校验异常计数应为 `0`。
- 读链路优化主要比较 `read` 场景的 `p95_seconds`、`p99_seconds`、`throughput_req_s`。
- 订单/支付/回调优化主要比较对应场景的 `business_ok` 和 SQL/MQ 异常数。

## 建议执行顺序

```text
A_round1 -> reset -> B_round1 -> reset
B_round2 -> reset -> A_round2 -> reset
A_round3 -> reset -> B_round3
```

交错执行可以降低机器温度、后台任务、JIT 预热等时间因素对单边版本的影响。
