# JuPin 测试脚本说明

本目录用于存放 JuPin 后端的三类测试脚本：HTTP 场景压测、业务一致性校验、MQ/可靠事件校验。

1. `http/run_api_scenarios.sh`
   - 用于执行 HTTP 场景测试和轻量压测。
   - 覆盖拼车详情读链路、无 Token 直打拦截、订单创建幂等、重复支付、重复 Mock 回调。

2. `sql/validate_consistency.sql`
   - 用于压测后的业务一致性校验。
   - 检查重复订单、重复占座、订单支付状态与成员状态一致性、拼车人数漂移、晚到回调状态回滚风险等。

3. `mq/validate_reliability.sql`
   - 用于 MQ 和可靠事件链路校验。
   - 当前可校验 `payment_event`，同时预留了未来 `outbox_event` 表的检查逻辑。

4. `ab/reset_state.sh`
   - 用于严格 A/B 前重建数据库并清空 Redis。
   - 每轮测试前执行，保证 A/B 使用同一套初始数据和缓存状态。

5. `ab/run_read_load.sh`
   - 用于严格 A/B 的拼车详情读链路压测。
   - 只请求读接口，不创建用户、车局或订单，避免写入副作用污染性能基线。

6. `ab/run_strict_ab_read.md`
   - 记录严格 A/B 的执行口径、命令和结果位置。

## 一、测试思路

这套脚本不是只看接口能不能返回 `200`，而是按“场景压测 + 业务校验 + 可靠性校验”的闭环来做。

HTTP 脚本负责模拟请求压力和异常请求：

- 拼车详情读链路：验证缓存链路在并发访问下的 P95 和 HTTP 成功数。
- 无 Token 直打创建订单：验证入口鉴权是否能拦截无资格流量。
- 订单创建幂等：同一用户、同一幂等 Key 连续创建订单，期望返回同一个订单号。
- 重复支付：同一订单连续支付两次，期望订单只支付一次，不重复占座。
- 重复 Mock 回调：同一渠道流水和回调请求重复提交，期望支付事件只处理一次。

其中读链路和写链路使用不同上下文：

- `POOL_ID` 只用于拼车详情读链路压测，通常传种子数据里的车局 ID。
- 写链路会自动注册或登录一个临时测试玩家，并发布一个临时玩家局，再对这个临时车局执行下单、支付和回调测试。
- 这样不会要求种子账号刚好处于“待支付”成员状态，也不会反复把种子车局占满。

SQL 脚本负责压测后验收业务结果：

- 接口返回 `200` 不代表业务一定正确，所以要继续查数据库。
- 重点看订单数、成员状态、拼车人数、支付事件流水是否一致。
- 核心异常计数应该为 `0`。

MQ/可靠性脚本负责验证异步链路：

- 当前项目主要检查 `payment_event` 是否有长期处理中、成功回调但订单未支付、逾期订单未释放成员等异常。
- 后续实现 Outbox 后，同一脚本会自动检查 pending、publish failed、compensation failed 等可靠消息指标。

## 二、如何控制人数和并发

当前 HTTP 脚本用两个参数控制压测规模：

- `REQUESTS`：总请求数，可以理解为本轮压测的样本数。
- `CONCURRENCY`：并发数，即同时发起多少个请求。

例如：

- `REQUESTS=100 CONCURRENCY=10`：总共请求 100 次，每批最多 10 个并发。
- `REQUESTS=500 CONCURRENCY=50`：总共请求 500 次，每批最多 50 个并发。

当前脚本里的并发主要用于“拼车详情读链路”。订单创建、支付、回调这些写链路目前是顺序执行，因为它们依赖同一个用户和同一个订单号。如果后续要做多人并发加入/支付，需要准备多组玩家账号，并扩展脚本按账号池循环登录和发起请求。

建议压测档位：

| 档位 | REQUESTS | CONCURRENCY | 用途 |
|---|---:|---:|---|
| L1 | 100 | 10 | 冒烟测试，确认脚本和服务可用 |
| L2 | 300 | 30 | 轻量压力，观察接口是否稳定 |
| L3 | 500 | 50 | 中等压力，观察 P95 是否抬升 |
| L4 | 1000 | 100 | 高压测试，观察服务是否进入退化区 |

## 三、验收指标

HTTP 脚本输出的关键指标：

| 指标 | 说明 | 期望 |
|---|---|---|
| `pool_detail_total` | 拼车详情总请求数 | 等于 `REQUESTS` |
| `pool_detail_http_200` | 拼车详情 HTTP 200 数 | 等于 `REQUESTS` 或接近 `REQUESTS` |
| `pool_detail_p95_seconds` | 拼车详情 P95 响应时间 | 越低越好，用于 A/B 对比 |
| `unauthorized_direct_create_http_status` | 无 Token 直打创建订单返回码 | 不应为 `200` |
| `write_user_phone` | 写链路使用的临时玩家手机号 | 用于追踪本轮测试产生的数据 |
| `write_pool_id` | 写链路创建的临时车局 ID | 后续订单、支付、回调都基于它执行 |
| `created_order_no` | 幂等创建得到的订单号 | 两次创建应相同，脚本会自动校验 |
| `duplicate_pay_order_no` | 重复支付订单号 | 两次支付都应返回业务成功 |
| `mock_callback_channel_txn_id` | 重复回调使用的渠道流水 | 数据库中应只处理一次 |

业务一致性 SQL 的核心验收项：

| 校验项 | 期望 |
|---|---:|
| `duplicate_order_by_user_idempotent_key` | 0 |
| `duplicate_order_by_user_pool_type` | 0 |
| `duplicate_pool_member_by_pool_user` | 0 |
| `paid_order_member_not_joined` | 0 |
| `joined_member_without_paid_deposit` | 0 |
| `pool_current_members_drift` | 0 |
| `overdue_order_member_still_pending_payment` | 0 |
| `duplicate_payment_event_key` | 0 |
| `duplicate_payment_request_no` | 0 |
| `duplicate_payment_channel_txn_id` | 0 |

MQ/可靠性 SQL 的核心验收项：

| 校验项 | 期望 |
|---|---:|
| `payment_event_processing_stuck_over_5_min` | 0 |
| `payment_event_success_order_not_paid` | 0 |
| `payment_event_ignored_order_paid` | 0 |
| `overdue_deposit_member_not_released` | 0 |
| `outbox_pending_over_5_min` | 0 |
| `outbox_publish_failed` | 0 |
| `outbox_compensation_failed` | 0 |

## 四、结果在哪里看

HTTP 脚本结果会直接输出在终端，例如：

```text
pool_detail_total=100
pool_detail_http_200=100
pool_detail_p95_seconds=0.0182
unauthorized_direct_create_http_status=401
created_order_no=...
duplicate_pay_order_no=...
mock_callback_channel_txn_id=...
```

拼车详情每次请求的原始耗时会写入临时文件：

```text
/tmp/jupin_pool_detail_${RUN_ID}.txt
```

每行格式是：

```text
响应耗时秒数 HTTP状态码
```

SQL 校验结果会直接输出在 MySQL 客户端。每一行包含：

- `check_name`：校验项名称。
- `anomaly_count`：异常数量。

验收时重点看 `anomaly_count` 是否为 `0`。

## 五、运行 HTTP 场景脚本

前置条件：

- 后端服务已启动，默认地址是 `http://localhost:8080`。
- 本机已安装 `curl` 和 `jq`。
- `POOL_ID` 对应的拼车详情接口需要能正常返回 `HTTP 200`。
- 写链路会自动准备临时玩家和临时车局，不需要手工把种子账号加入车局。

示例：

```bash
BASE_URL=http://localhost:8080 \
PLAYER_PHONE=13812340001 \
PLAYER_PASSWORD=player123 \
POOL_ID=1 \
REQUESTS=100 \
CONCURRENCY=10 \
./Test/http/run_api_scenarios.sh
```

可以指定写链路使用的临时手机号，方便在数据库中追踪本轮测试数据：

```bash
BASE_URL=http://localhost:8080 \
PLAYER_PHONE=13812340001 \
PLAYER_PASSWORD=player123 \
WRITE_PHONE=13999990001 \
WRITE_PASSWORD=player123 \
POOL_ID=1 \
REQUESTS=100 \
CONCURRENCY=10 \
./Test/http/run_api_scenarios.sh
```

也可以直接传入已经登录得到的 Token：

```bash
PLAYER_TOKEN=eyJ... POOL_ID=1 ./Test/http/run_api_scenarios.sh
```

## 六、运行业务一致性校验 SQL

HTTP 测试完成后执行：

```bash
mysql -h127.0.0.1 -uroot -p jupin < Test/sql/validate_consistency.sql
```

核心异常计数期望为 `0`，尤其是：

- `duplicate_order_by_user_idempotent_key`
- `duplicate_order_by_user_pool_type`
- `paid_order_member_not_joined`
- `joined_member_without_paid_deposit`
- `pool_current_members_drift`
- `duplicate_payment_event_key`

## 七、运行 MQ/可靠性校验 SQL

超时、重复回调或 MQ 相关测试完成后执行：

```bash
mysql -h127.0.0.1 -uroot -p jupin < Test/mq/validate_reliability.sql
```

当前项目可以校验 `payment_event` 可靠性。Outbox 检查使用动态 SQL 做了表存在判断，所以在 Outbox 表还没实现前也可以运行。

## 八、严格 A/B 读链路压测

严格 A/B 不使用 `http/run_api_scenarios.sh` 直接对比性能，因为该脚本包含注册、发布车局、下单、支付等写入链路，会改变数据库状态。

严格 A/B 使用独立读链路脚本：

```bash
bash Test/ab/reset_state.sh
LABEL=A_round1 \
BASE_URL=http://localhost:8080 \
POOL_ID=1 \
REQUESTS=300 \
CONCURRENCY=30 \
bash Test/ab/run_read_load.sh
```

切换到 B 版本并重启后端后，再执行：

```bash
bash Test/ab/reset_state.sh
LABEL=B_round1 \
BASE_URL=http://localhost:8080 \
POOL_ID=1 \
REQUESTS=300 \
CONCURRENCY=30 \
bash Test/ab/run_read_load.sh
```

每个版本至少执行 3 轮，取 `p95_seconds` 中位数作为主指标，辅助观察 `p99_seconds` 和 `throughput_req_s`。详细流程见 `Test/ab/run_strict_ab_read.md`。
