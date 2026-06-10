# JuPin 测试脚本说明

当前有效测试方案是 `Test/ab/` 下的第三版 A/B 梯度压测框架。早期 `bench/`、`http/`、`mq/`、`sql/` 和 `results/` 方案已废弃，不再作为测试入口。

## 有效脚本

```text
Test/ab/
├── ab_suite.sh            # 单档 A/B 入口
├── ab_pressure_suite.sh   # 梯度 A/B 压测入口
├── ab_common.sh           # A/B 编排：切分支、构建重启、reset、聚合
├── common.sh              # HTTP、登录、统计等公共函数
├── reset_state.sh         # 本地 MySQL + Redis reset
├── ab_oversell.sh         # 支付占座超员防护 A/B
├── ab_idempotent.sh       # 订单/支付幂等 A/B
├── ab_cache.sh            # 拼车详情缓存与穿透 A/B
└── README.md              # 详细测试方案
```

## 本地 A/B 梯度压测

```bash
AB_ROUNDS=3 \
AB_WARMUP=1 \
AB_RESET_EACH_ROUND=true \
bash Test/ab/ab_pressure_suite.sh
```

只跑某一项：

```bash
TESTS=oversell bash Test/ab/ab_pressure_suite.sh
TESTS=idempotent bash Test/ab/ab_pressure_suite.sh
TESTS=cache bash Test/ab/ab_pressure_suite.sh
```

## 远程服务器 A/B 梯度压测

远程模式仍然是严格 A/B：每个压力档都会先切到 baseline 分支跑 A，再切回 `cleanup-bench-review` 跑 B，并输出该档 comparison。

```bash
AB_BACKEND_MODE=remote \
REMOTE_SSH=tecent_server \
REMOTE_PROJECT_DIR=~/JuPin \
BASE_URL=http://124.221.242.32:8080 \
TESTS=cache \
AB_ROUNDS=3 \
AB_WARMUP=1 \
AB_RESET_EACH_ROUND=true \
CACHE_LEVELS=1000:100:1,2000:200:1,5000:300:1,10000:500:1 \
bash Test/ab/ab_pressure_suite.sh
```

档位统一采用 `用户档:并发档:业务参数` 输入：

```text
OVERSELL_LEVELS=用户数:并发数:座位数
IDEMPOTENT_LEVELS=请求数:并发数
CACHE_LEVELS=请求数:并发数:poolId
```

远程模式会自动执行：

- `git checkout` 切换 A/B 分支；
- `docker-compose up -d --build app` 构建并重启后端；
- 每轮 reset 远程 MySQL 和 Redis；
- 本机向远程 `BASE_URL` 发压测请求并保存结果。

## 结果目录

结果默认写入：

```text
Test/ab_results/
```

该目录是压测产物，不作为源码提交。
