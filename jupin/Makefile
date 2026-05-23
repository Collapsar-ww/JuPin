.PHONY: build up down restart logs ps clean

# 构建所有镜像
build:
	docker compose build

# 启动所有服务（后台）
up:
	docker compose up -d
	@echo ""
	@echo "  App API:     http://localhost:${APP_PORT}"
	@echo "  Swagger UI:  http://localhost:${APP_PORT}/swagger-ui.html"
	@echo "  RabbitMQ:    http://localhost:15672 (guest/guest)"

# 停止并清理所有服务
down:
	docker compose down

# 重启所有服务
restart: down up

# 实时查看日志
logs:
	docker compose logs -f

# 查看服务状态
ps:
	docker compose ps

# 查看特定服务的日志
logs-app:
	docker compose logs -f app

logs-mysql:
	docker compose logs -f mysql

# 停止并删除数据卷（⚠️ 会丢失所有数据）
clean:
	docker compose down -v
