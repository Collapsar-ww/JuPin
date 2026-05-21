-- ============================================================
-- JuPin 剧本杀拼车系统 - MySQL 初始化脚本
-- 与当前 Entity 字段保持一致
-- ============================================================

CREATE DATABASE IF NOT EXISTS `script_murder_carpool`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `script_murder_carpool`;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `credit_log`;
DROP TABLE IF EXISTS `message`;
DROP TABLE IF EXISTS `review`;
DROP TABLE IF EXISTS `order`;
DROP TABLE IF EXISTS `pool_member`;
DROP TABLE IF EXISTS `car_pool`;
DROP TABLE IF EXISTS `shop_script`;
DROP TABLE IF EXISTS `script`;
DROP TABLE IF EXISTS `shop_member`;
DROP TABLE IF EXISTS `shop`;
DROP TABLE IF EXISTS `user`;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `phone` VARCHAR(20) NOT NULL,
  `password` VARCHAR(255) NOT NULL,
  `nickname` VARCHAR(50) NOT NULL,
  `avatar` VARCHAR(255) DEFAULT NULL,
  `gender` TINYINT DEFAULT 0 COMMENT '0-未知 1-男 2-女',
  `role` TINYINT DEFAULT 0 COMMENT '0-玩家 1-店家 2-管理员',
  `city` VARCHAR(50) DEFAULT NULL,
  `preference` VARCHAR(255) DEFAULT NULL,
  `credit_score` INT DEFAULT 100,
  `status` TINYINT DEFAULT 1 COMMENT '0-禁用 1-正常',
  `register_ip` VARCHAR(45) DEFAULT NULL,
  `last_login_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_role_status` (`role`, `status`),
  KEY `idx_city` (`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE `shop` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `address` VARCHAR(255) DEFAULT NULL,
  `phone` VARCHAR(20) DEFAULT NULL,
  `logo` VARCHAR(255) DEFAULT NULL,
  `cover` VARCHAR(255) DEFAULT NULL,
  `description` TEXT DEFAULT NULL,
  `opening_hours` VARCHAR(50) DEFAULT NULL,
  `city` VARCHAR(50) DEFAULT NULL,
  `status` TINYINT DEFAULT 1 COMMENT '0-关闭 1-营业',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_city_status` (`city`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺表';

CREATE TABLE `shop_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `role` TINYINT DEFAULT 3 COMMENT '1-店长 2-管理员 3-普通成员',
  `status` TINYINT DEFAULT 1 COMMENT '1-已加入',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_user` (`shop_id`, `user_id`),
  KEY `idx_user` (`user_id`),
  CONSTRAINT `fk_shop_member_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_shop_member_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺成员表';

CREATE TABLE `script` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `type` VARCHAR(50) DEFAULT NULL,
  `difficulty` TINYINT DEFAULT 1 COMMENT '1-简单 2-中等 3-困难',
  `min_players` INT DEFAULT NULL,
  `max_players` INT DEFAULT NULL,
  `duration` INT DEFAULT NULL COMMENT '参考时长(分钟)',
  `roles` TEXT DEFAULT NULL,
  `cover` VARCHAR(255) DEFAULT NULL,
  `price_ref` DECIMAL(10,2) DEFAULT NULL,
  `description` TEXT DEFAULT NULL,
  `status` TINYINT DEFAULT 1 COMMENT '0-下架 1-上架',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_type_status` (`type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剧本表';

CREATE TABLE `shop_script` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `shop_id` BIGINT NOT NULL,
  `script_id` BIGINT NOT NULL,
  `price` DECIMAL(10,2) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_script` (`shop_id`, `script_id`),
  KEY `idx_script` (`script_id`),
  CONSTRAINT `fk_shop_script_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_shop_script_script` FOREIGN KEY (`script_id`) REFERENCES `script` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺剧本关联表';

CREATE TABLE `car_pool` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `type` TINYINT DEFAULT 0 COMMENT '0-玩家局 1-店家局',
  `owner_id` BIGINT NOT NULL,
  `shop_id` BIGINT DEFAULT NULL,
  `script_id` BIGINT DEFAULT NULL,
  `script_name` VARCHAR(100) NOT NULL,
  `script_type` VARCHAR(50) DEFAULT NULL,
  `roles` TEXT DEFAULT NULL,
  `city` VARCHAR(50) NOT NULL,
  `address` VARCHAR(255) DEFAULT NULL,
  `start_time` DATETIME NOT NULL,
  `end_time` DATETIME DEFAULT NULL,
  `max_members` INT NOT NULL,
  `current_members` INT DEFAULT 0,
  `price` DECIMAL(10,2) DEFAULT 0.00,
  `deposit` DECIMAL(10,2) DEFAULT 10.00,
  `dm_id` BIGINT DEFAULT NULL,
  `join_type` TINYINT DEFAULT 0 COMMENT '0-审核制 1-自动通过',
  `status` TINYINT DEFAULT 0 COMMENT '0-开放 1-满员 2-拼车成功 3-剧本杀完成 4-已取消',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_start_time` (`status`, `start_time`),
  KEY `idx_city_status` (`city`, `status`),
  KEY `idx_owner` (`owner_id`),
  KEY `idx_shop` (`shop_id`),
  CONSTRAINT `fk_pool_owner` FOREIGN KEY (`owner_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pool_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_pool_script` FOREIGN KEY (`script_id`) REFERENCES `script` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼车表';

CREATE TABLE `pool_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `pool_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `role` TINYINT DEFAULT 0 COMMENT '0-玩家 1-发布人',
  `selected_role` VARCHAR(50) DEFAULT NULL,
  `status` TINYINT DEFAULT 0 COMMENT '0-待审核 1-待支付 2-已加入 3-已退出 4-已拒绝',
  `completed_confirmed` TINYINT DEFAULT 0 COMMENT '0-未确认 1-已确认 2-已拒绝',
  `completed_confirm_time` DATETIME DEFAULT NULL,
  `finished_confirmed` TINYINT DEFAULT 0 COMMENT '0-未确认 1-已确认 2-已拒绝',
  `finished_confirm_time` DATETIME DEFAULT NULL,
  `join_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `leave_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pool_user` (`pool_id`, `user_id`),
  KEY `idx_pool_status` (`pool_id`, `status`),
  KEY `idx_user_status` (`user_id`, `status`),
  CONSTRAINT `fk_member_pool` FOREIGN KEY (`pool_id`) REFERENCES `car_pool` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_member_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼车成员表';

CREATE TABLE `order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_no` VARCHAR(32) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `pool_id` BIGINT NOT NULL,
  `type` TINYINT DEFAULT 0 COMMENT '0-押金 1-车费',
  `amount` DECIMAL(10,2) NOT NULL,
  `status` TINYINT DEFAULT 0 COMMENT '0-待支付 1-已支付 2-已退款 3-已扣留 4-逾期',
  `payee_id` BIGINT DEFAULT NULL,
  `payee_type` TINYINT DEFAULT NULL COMMENT '0-DM 1-店铺',
  `release_status` TINYINT DEFAULT 0 COMMENT '0-未释放 1-已释放',
  `release_time` DATETIME DEFAULT NULL,
  `refund_reason` VARCHAR(255) DEFAULT NULL,
  `channel_txn_id` VARCHAR(64) DEFAULT NULL,
  `pay_time` DATETIME DEFAULT NULL,
  `refund_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_user_pool_type` (`user_id`, `pool_id`, `type`),
  KEY `idx_pool` (`pool_id`),
  KEY `idx_status_create` (`status`, `create_time`),
  CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_order_pool` FOREIGN KEY (`pool_id`) REFERENCES `car_pool` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

CREATE TABLE `review` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `pool_id` BIGINT NOT NULL,
  `from_user_id` BIGINT NOT NULL,
  `target_id` BIGINT NOT NULL COMMENT 'type=0存shop_id，type=1存dm_user_id',
  `type` TINYINT DEFAULT 0 COMMENT '0-评价店家 1-评价DM',
  `score` TINYINT NOT NULL COMMENT '1-5',
  `content` VARCHAR(500) DEFAULT NULL,
  `tags` VARCHAR(100) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_unique` (`pool_id`, `from_user_id`, `type`),
  KEY `idx_target_type` (`target_id`, `type`),
  CONSTRAINT `fk_review_pool` FOREIGN KEY (`pool_id`) REFERENCES `car_pool` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_review_from` FOREIGN KEY (`from_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价表';

CREATE TABLE `message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `msg_key` VARCHAR(64) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `type` TINYINT DEFAULT 0 COMMENT '0-系统 1-匹配 2-成团 3-跳车 4-评价',
  `title` VARCHAR(100) NOT NULL,
  `content` VARCHAR(500) DEFAULT NULL,
  `related_id` BIGINT DEFAULT NULL,
  `is_read` TINYINT DEFAULT 0,
  `read_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_key` (`msg_key`),
  KEY `idx_user_type_read` (`user_id`, `type`, `is_read`),
  CONSTRAINT `fk_msg_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

CREATE TABLE `credit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `change` INT NOT NULL,
  `balance` INT NOT NULL,
  `reason` VARCHAR(255) NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  CONSTRAINT `fk_credit_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用分日志';

INSERT INTO `script` (`name`, `type`, `difficulty`, `min_players`, `max_players`, `duration`, `roles`, `price_ref`, `description`) VALUES
('年轮', '硬核', 2, 4, 6, 240, '[{"name":"侦探","desc":"推理位"}]', 88.00, '硬核推理本'),
('古木吟', '情感', 2, 5, 6, 240, '[{"name":"瘦小女"},{"name":"冷俊男"}]', 78.00, '情感沉浸本'),
('拆迁', '欢乐', 1, 6, 8, 180, '[{"name":"包租公"},{"name":"包租婆"}]', 68.00, '欢乐机制本');
