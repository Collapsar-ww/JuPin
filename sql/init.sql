-- ============================================================
-- 剧本杀拼车系统 — 数据库初始化脚本 (v2.5)
-- 适用：MySQL 8.0+
-- ============================================================

DROP DATABASE IF EXISTS `script_murder_carpool`;
CREATE DATABASE `script_murder_carpool`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `script_murder_carpool`;

-- ------------------------------------------------------------
-- 1. 用户表
-- ------------------------------------------------------------
CREATE TABLE `user` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `phone`        VARCHAR(20)  NOT NULL,
  `password`     VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密',
  `nickname`     VARCHAR(50)  NOT NULL,
  `avatar`       VARCHAR(255) DEFAULT NULL,
  `gender`       TINYINT      DEFAULT 0 COMMENT '0-未知 1-男 2-女',
  `role`         TINYINT      DEFAULT 0 COMMENT '0-玩家 1-店家 2-管理员',
  `city`         VARCHAR(50)  DEFAULT NULL,
  `preference`   VARCHAR(255) DEFAULT NULL COMMENT '剧本偏好，逗号分隔',
  `credit_score` INT          DEFAULT 100 COMMENT '信用分 0-100',
  `status`       TINYINT      DEFAULT 1 COMMENT '0-禁用 1-正常',
  `register_ip`  VARCHAR(45)  DEFAULT NULL,
  `last_login_time` DATETIME  DEFAULT NULL,
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_city` (`city`),
  KEY `idx_role` (`role`),
  KEY `idx_credit_score` (`credit_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. 店铺表
-- ------------------------------------------------------------
CREATE TABLE `shop` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(100) NOT NULL,
  `address`       VARCHAR(255) DEFAULT NULL,
  `phone`         VARCHAR(20)  DEFAULT NULL,
  `logo`          VARCHAR(255) DEFAULT NULL,
  `cover`         VARCHAR(255) DEFAULT NULL,
  `description`   TEXT         DEFAULT NULL,
  `opening_hours` VARCHAR(50)  DEFAULT NULL,
  `city`          VARCHAR(50)  DEFAULT NULL,
  `status`        TINYINT      DEFAULT 1 COMMENT '0-关闭 1-营业',
  `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_city` (`city`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺表';

-- ------------------------------------------------------------
-- 3. 店铺成员表
-- ------------------------------------------------------------
CREATE TABLE `shop_member` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `shop_id`     BIGINT   NOT NULL,
  `user_id`     BIGINT   NOT NULL,
  `role`        TINYINT  DEFAULT 3 COMMENT '1-店长 2-管理员 3-普通成员',
  `status`      TINYINT  DEFAULT 1 COMMENT '1-已加入',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_user` (`shop_id`, `user_id`),
  KEY `idx_user` (`user_id`),
  CONSTRAINT `fk_sm_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_sm_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺成员表';

-- ------------------------------------------------------------
-- 4. 剧本表
-- ------------------------------------------------------------
CREATE TABLE `script` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `name`         VARCHAR(100) NOT NULL,
  `type`         VARCHAR(50)  DEFAULT NULL COMMENT '硬核/情感/欢乐/恐怖/机制',
  `difficulty`   TINYINT      DEFAULT 1 COMMENT '1-简单 2-中等 3-困难',
  `min_players`  INT          DEFAULT NULL,
  `max_players`  INT          DEFAULT NULL,
  `duration`     INT          DEFAULT NULL COMMENT '参考时长(分钟)',
  `roles`        TEXT         DEFAULT NULL COMMENT '角色列表JSON',
  `cover`        VARCHAR(255) DEFAULT NULL COMMENT '封面图',
  `price_ref`    DECIMAL(10,2) DEFAULT NULL COMMENT '价格参考',
  `description`  TEXT         DEFAULT NULL COMMENT '剧本简介',
  `status`       TINYINT      DEFAULT 1 COMMENT '0-下架 1-上架',
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_type` (`type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='剧本表';

-- ------------------------------------------------------------
-- 5. 店铺剧本关联表
-- ------------------------------------------------------------
CREATE TABLE `shop_script` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `shop_id`     BIGINT        NOT NULL,
  `script_id`   BIGINT        NOT NULL,
  `price`       DECIMAL(10,2) DEFAULT NULL COMMENT '店内定价',
  `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_script` (`shop_id`, `script_id`),
  CONSTRAINT `fk_ss_shop`   FOREIGN KEY (`shop_id`)   REFERENCES `shop`   (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ss_script` FOREIGN KEY (`script_id`) REFERENCES `script` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺剧本关联表';

-- ------------------------------------------------------------
-- 6. 拼车表
-- ------------------------------------------------------------
CREATE TABLE `car_pool` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT,
  `type`            TINYINT       DEFAULT 0 COMMENT '0-玩家局 1-店家局',
  `owner_id`        BIGINT        NOT NULL COMMENT '发布人',
  `shop_id`         BIGINT        DEFAULT NULL COMMENT '店家局时关联店铺',
  `script_id`       BIGINT        DEFAULT NULL COMMENT '关联剧本ID',
  `script_name`     VARCHAR(100)  DEFAULT NULL COMMENT '冗余：剧本名称',
  `script_type`     VARCHAR(50)   DEFAULT NULL COMMENT '冗余：剧本类型',
  `roles`           TEXT          DEFAULT NULL COMMENT '角色列表JSON',
  `city`            VARCHAR(50)   NOT NULL,
  `address`         VARCHAR(255)  DEFAULT NULL,
  `start_time`      DATETIME      NOT NULL,
  `end_time`        DATETIME      DEFAULT NULL,
  `max_members`     INT           NOT NULL COMMENT '总需人数',
  `current_members` INT           DEFAULT 0 COMMENT '已支付押金的正式成员数',
  `price`           DECIMAL(10,2) DEFAULT 0.00 COMMENT '人均总费用',
  `deposit`         DECIMAL(10,2) DEFAULT 10.00 COMMENT '预付押金（含在总费用内）',
  `dm_id`           BIGINT        DEFAULT NULL COMMENT 'DM用户ID',
  `join_type`       TINYINT       DEFAULT 0 COMMENT '0-审核制 1-自动通过',
  `status`          TINYINT       DEFAULT 0 COMMENT '0-开放 1-满员 2-拼车成功(COMPLETED) 3-完成(FINISHED) 4-已取消',
  `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_owner` (`owner_id`),
  KEY `idx_shop` (`shop_id`),
  KEY `idx_city_status` (`city`, `status`),
  KEY `idx_status_start_time` (`status`, `start_time`),
  KEY `idx_type_status` (`type`, `status`),
  CONSTRAINT `fk_pool_owner` FOREIGN KEY (`owner_id`) REFERENCES `user`   (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pool_shop`  FOREIGN KEY (`shop_id`)  REFERENCES `shop`   (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pool_script` FOREIGN KEY (`script_id`) REFERENCES `script` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拼车表';

-- ------------------------------------------------------------
-- 7. 拼车成员表
-- ------------------------------------------------------------
CREATE TABLE `pool_member` (
  `id`                      BIGINT      NOT NULL AUTO_INCREMENT,
  `pool_id`                 BIGINT      NOT NULL,
  `user_id`                 BIGINT      NOT NULL,
  `role`                    TINYINT     DEFAULT 0 COMMENT '0-玩家 1-发布人',
  `selected_role`           VARCHAR(50) DEFAULT NULL COMMENT '已选剧本角色名',
  `status`                  TINYINT     DEFAULT 0 COMMENT '0-待审核 1-待支付 2-已加入 3-已退出(跳车) 4-已拒绝',
  `completed_confirmed`     TINYINT     DEFAULT 0 COMMENT 'COMPLETED确认: 0-未确认 1-已确认 2-已拒绝',
  `completed_confirm_time`  DATETIME    DEFAULT NULL,
  `finished_confirmed`      TINYINT     DEFAULT 0 COMMENT 'FINISHED确认: 0-未确认 1-已确认 2-已拒绝',
  `finished_confirm_time`   DATETIME    DEFAULT NULL,
  `join_time`               DATETIME    DEFAULT CURRENT_TIMESTAMP,
  `leave_time`              DATETIME    DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pool_user` (`pool_id`, `user_id`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_pool_status` (`pool_id`, `status`),
  CONSTRAINT `fk_pm_pool` FOREIGN KEY (`pool_id`) REFERENCES `car_pool` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pm_user` FOREIGN KEY (`user_id`) REFERENCES `user`     (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='拼车成员表';

-- ------------------------------------------------------------
-- 8. 订单表
-- ------------------------------------------------------------
CREATE TABLE `order` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT,
  `order_no`        VARCHAR(32)   NOT NULL COMMENT '雪花算法生成',
  `user_id`         BIGINT        NOT NULL,
  `pool_id`         BIGINT        NOT NULL,
  `type`            TINYINT       DEFAULT 0 COMMENT '0-押金 1-车费',
  `amount`          DECIMAL(10,2) NOT NULL,
  `status`          TINYINT       DEFAULT 0 COMMENT '0-待支付 1-已支付 2-已退款 3-已扣留 4-逾期',
  `payee_id`        BIGINT        DEFAULT NULL COMMENT '收款方ID（DM或店铺）',
  `payee_type`      TINYINT       DEFAULT NULL COMMENT '收款方类型: 0-DM(个人) 1-店铺',
  `release_status`  TINYINT       DEFAULT 0 COMMENT '0-未释放 1-已释放',
  `release_time`    DATETIME      DEFAULT NULL,
  `refund_reason`   VARCHAR(255)  DEFAULT NULL,
  `channel_txn_id`  VARCHAR(64)   DEFAULT NULL COMMENT '支付渠道流水号(Mock为空)',
  `pay_time`        DATETIME      DEFAULT NULL,
  `refund_time`     DATETIME      DEFAULT NULL,
  `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user` (`user_id`),
  KEY `idx_pool` (`pool_id`),
  KEY `idx_user_pool_type` (`user_id`, `pool_id`, `type`),
  KEY `idx_payee` (`payee_id`, `payee_type`),
  KEY `idx_release` (`release_status`, `status`),
  CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `user`     (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_order_pool` FOREIGN KEY (`pool_id`) REFERENCES `car_pool` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- ------------------------------------------------------------
-- 9. 评价表
-- ------------------------------------------------------------
CREATE TABLE `review` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `pool_id`      BIGINT       NOT NULL,
  `from_user_id` BIGINT       NOT NULL COMMENT '评价人',
  `target_id`    BIGINT       NOT NULL COMMENT 'type=0存shop_id, type=1存dm_user_id',
  `type`         TINYINT      DEFAULT 0 COMMENT '0-评价店家 1-评价DM',
  `score`        TINYINT      NOT NULL COMMENT '1-5',
  `content`      VARCHAR(500) DEFAULT NULL,
  `tags`         VARCHAR(100) DEFAULT NULL,
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_unique` (`pool_id`, `from_user_id`, `type`),
  KEY `idx_target` (`target_id`, `type`),
  CONSTRAINT `fk_rv_pool` FOREIGN KEY (`pool_id`)      REFERENCES `car_pool` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rv_from` FOREIGN KEY (`from_user_id`) REFERENCES `user`     (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价表';

-- ------------------------------------------------------------
-- 10. 消息表
-- ------------------------------------------------------------
CREATE TABLE `message` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `msg_key`     VARCHAR(64)  NOT NULL COMMENT '消息幂等键',
  `user_id`     BIGINT       NOT NULL COMMENT '接收人',
  `type`        TINYINT      DEFAULT 0 COMMENT '0-系统 1-匹配 2-成团 3-跳车 4-评价',
  `title`       VARCHAR(100) NOT NULL,
  `content`     VARCHAR(500) DEFAULT NULL,
  `related_id`  BIGINT       DEFAULT NULL,
  `is_read`     TINYINT      DEFAULT 0,
  `read_time`   DATETIME     DEFAULT NULL,
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_key` (`msg_key`),
  KEY `idx_user_read` (`user_id`, `is_read`),
  KEY `idx_user_type_read` (`user_id`, `type`, `is_read`),
  CONSTRAINT `fk_msg_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- ------------------------------------------------------------
-- 11. 信用分变更日志
-- ------------------------------------------------------------
CREATE TABLE `credit_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL,
  `change`      INT          NOT NULL COMMENT '变动值',
  `balance`     INT          NOT NULL COMMENT '变动后余额',
  `reason`      VARCHAR(255) NOT NULL,
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  CONSTRAINT `fk_cl_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='信用分日志';

-- ------------------------------------------------------------
-- 12. 玩家偏好表
-- ------------------------------------------------------------
CREATE TABLE `player_preference` (
  `id`           BIGINT         NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT         NOT NULL,
  `city`         VARCHAR(50)    DEFAULT NULL,
  `script_type`  VARCHAR(50)    DEFAULT NULL COMMENT '剧本类型偏好（V0单选）',
  `price_min`    DECIMAL(10,2)  DEFAULT NULL,
  `price_max`    DECIMAL(10,2)  DEFAULT NULL,
  `time_slot`    VARCHAR(50)    DEFAULT NULL COMMENT '常玩时间段：WEEKEND_NIGHT等',
  `min_members`  INT            DEFAULT NULL,
  `max_members`  INT            DEFAULT NULL,
  `create_time`  DATETIME       DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`),
  CONSTRAINT `fk_pp_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家偏好表';

-- ------------------------------------------------------------
-- 13. 群聊消息表
-- ------------------------------------------------------------
CREATE TABLE `chat_message` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `pool_id`     BIGINT       NOT NULL,
  `sender_id`   BIGINT       NOT NULL,
  `sender_name` VARCHAR(50)  DEFAULT NULL,
  `sender_role` VARCHAR(20)  DEFAULT NULL COMMENT 'player/shop',
  `content`     VARCHAR(1000) NOT NULL,
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pool_create` (`pool_id`, `create_time`),
  CONSTRAINT `fk_cm_pool` FOREIGN KEY (`pool_id`) REFERENCES `car_pool` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cm_user` FOREIGN KEY (`sender_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群聊消息表';

-- ------------------------------------------------------------
-- 初始种子数据
-- ------------------------------------------------------------
INSERT INTO `script`
(`id`, `name`, `type`, `difficulty`, `min_players`, `max_players`, `duration`, `roles`, `price_ref`, `description`, `status`)
VALUES
(1, '接口测试剧本', '硬核', 2, 2, 6, 240, '[{"name":"侦探","desc":"推理位"},{"name":"医生","desc":"辅助位"}]', 100.00, '接口测试用默认剧本', 1),
(2, '古木吟', '情感', 2, 5, 6, 240, '[{"name":"瘦小女"},{"name":"冷俊男"}]', 78.00, '情感沉浸本', 1),
(3, '拆迁', '欢乐', 1, 6, 8, 180, '[{"name":"包租公"},{"name":"包租婆"}]', 68.00, '欢乐机制本', 1);

INSERT INTO `user` (`phone`, `password`, `nickname`, `gender`, `role`, `city`, `preference`, `credit_score`) VALUES
('13800000001', '$2a$10$dummy_hash_placeholder', '剧本杀手',   1, 0, '上海', '硬核,情感,欢乐', 95),
('13800000002', '$2a$10$dummy_hash_placeholder', '推理迷',     2, 0, '上海', '硬核,恐怖', 88),
('13800000003', '$2a$10$dummy_hash_placeholder', '欢乐玩家',   1, 0, '北京', '欢乐,机制', 72),
('13800000004', '$2a$10$dummy_hash_placeholder', '鸽子王',     2, 0, '深圳', '情感,欢乐', 45),
('13800000005', '$2a$10$dummy_hash_placeholder', '硬核推土机', 1, 0, '上海', '硬核,机制', 100),
('13900000001', '$2a$10$dummy_hash_placeholder', '桌游店长',   1, 1, '上海', NULL, 100),
('13900000002', '$2a$10$dummy_hash_placeholder', '推理社店员', 2, 1, '北京', NULL, 100),
('admin001',    '$2a$10$dummy_hash_placeholder', '系统管理员', 0, 2, NULL, NULL, 100);

INSERT INTO `shop` (`name`, `address`, `phone`, `city`, `description`, `opening_hours`, `status`) VALUES
('静安剧本杀馆',  '上海市静安区南京西路XXX号', '021-12345678', '上海', '上海最专业的剧本杀体验馆', '10:00-22:00', 1),
('衡山路推理社',  '上海市徐汇区衡山路YYY号',  '021-87654321', '上海', '沉浸式剧本杀体验', '12:00-24:00', 1),
('朝阳剧本杀俱乐部', '北京市朝阳区三里屯ZZZ号', '010-12345678', '北京', '北京人气剧本杀店', '10:00-23:00', 1);

INSERT INTO `shop_member` (`shop_id`, `user_id`, `role`) VALUES
(1, 6, 1),
(2, 7, 1),
(3, 6, 3);
