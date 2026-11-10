-- TripAgent V2 数据库初始化脚本（MySQL 8）
-- 对应 docs/project/DATABASE.md 的当前代码基线。
-- 注意：本脚本用于全新环境；已有数据的库请先评估再执行。

CREATE DATABASE IF NOT EXISTS `tripagent`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `tripagent`;

-- ============================================================
-- 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL COMMENT '用户ID（雪花）',
  `phone` VARCHAR(20) NOT NULL COMMENT '手机号（登录标识）',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt密码哈希',
  `nickname` VARCHAR(32) NULL COMMENT '昵称（空时用户+手机号后4位）',
  `username` VARCHAR(32) NULL COMMENT '用户名（预留，可空）',
  `email` VARCHAR(128) NULL COMMENT '邮箱',
  `avatar_url` VARCHAR(512) NULL COMMENT '头像地址',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1-正常 0-禁用',
  `last_login_time` DATETIME NULL COMMENT '最后登录时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- ============================================================
-- 用户偏好表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user_preference` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `crowd` VARCHAR(20) NOT NULL DEFAULT 'general' COMMENT '常用同行人群',
  `budget_level` VARCHAR(20) NOT NULL DEFAULT 'economy' COMMENT '预算档位',
  `preferred_transport` VARCHAR(20) NOT NULL DEFAULT 'transit' COMMENT '偏好交通方式',
  `travel_pace` VARCHAR(20) NOT NULL DEFAULT 'moderate' COMMENT '行程节奏',
  `interests` JSON NULL COMMENT '兴趣标签数组',
  `food_preferences` JSON NULL COMMENT '饮食偏好数组',
  `accommodation_preference` VARCHAR(255) NULL COMMENT '住宿偏好',
  `notes` VARCHAR(1000) NULL COMMENT '补充说明',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户出行偏好';

-- ============================================================
-- 行程主表
-- ============================================================
CREATE TABLE IF NOT EXISTS `trip` (
  `id` BIGINT NOT NULL COMMENT '行程ID（雪花）',
  `user_id` BIGINT NOT NULL COMMENT '归属用户',
  `title` VARCHAR(100) NOT NULL COMMENT '行程名称',
  `destination` VARCHAR(64) NOT NULL COMMENT '目的地城市',
  `start_date` DATE NOT NULL COMMENT '开始日期',
  `end_date` DATE NOT NULL COMMENT '结束日期',
  `days_count` INT NOT NULL COMMENT '天数',
  `description` VARCHAR(1000) NULL COMMENT '行程描述',
  `source` VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT 'AGENT/MANUAL',
  `plan_extras` JSON NULL COMMENT '住宿/餐饮/预算/假设/校验结果',
  `idempotency_key` VARCHAR(64) NULL COMMENT 'Agent创建幂等键',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_idempotency` (`user_id`, `idempotency_key`),
  KEY `idx_user_deleted_update` (`user_id`, `deleted`, `update_time`),
  KEY `idx_user_destination` (`user_id`, `destination`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='行程主表';

-- ============================================================
-- 行程每日表
-- ============================================================
CREATE TABLE IF NOT EXISTS `trip_day` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `trip_id` BIGINT NOT NULL COMMENT '行程ID',
  `day_index` INT NOT NULL COMMENT '第几天，从1开始',
  `day_date` DATE NOT NULL COMMENT '对应日期',
  `theme` VARCHAR(100) NULL COMMENT '当天主题',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trip_day` (`trip_id`, `day_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='行程每日信息';

-- ============================================================
-- 行程项表
-- ============================================================
CREATE TABLE IF NOT EXISTS `trip_item` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `trip_id` BIGINT NOT NULL COMMENT '行程ID',
  `trip_day_id` BIGINT NOT NULL COMMENT '所属天ID',
  `sequence_no` INT NOT NULL COMMENT '当天顺序，从1开始',
  `item_type` VARCHAR(20) NOT NULL DEFAULT 'ATTRACTION' COMMENT '行程项类型',
  `name` VARCHAR(128) NOT NULL COMMENT '景点/活动名称',
  `address` VARCHAR(255) NULL COMMENT '地址',
  `longitude` DECIMAL(10,6) NULL COMMENT '经度',
  `latitude` DECIMAL(10,6) NULL COMMENT '纬度',
  `time_slot` VARCHAR(16) NULL COMMENT '上午/中午/下午/晚上',
  `duration_min` INT NULL COMMENT '预计时长（分钟）',
  `reason` VARCHAR(500) NULL COMMENT '安排理由',
  `external_poi_id` VARCHAR(64) NULL COMMENT '高德POI ID',
  `notes` VARCHAR(500) NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_day_sequence` (`trip_day_id`, `sequence_no`),
  KEY `idx_trip_id` (`trip_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='行程项';

-- ============================================================
-- 收藏表
-- ============================================================
CREATE TABLE IF NOT EXISTS `favorite_place` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '归属用户',
  `place_key` VARCHAR(160) NOT NULL COMMENT '地点稳定标识',
  `external_poi_id` VARCHAR(64) NULL COMMENT '高德POI ID',
  `name` VARCHAR(128) NOT NULL COMMENT '地点名称',
  `city` VARCHAR(64) NULL COMMENT '城市',
  `address` VARCHAR(255) NULL COMMENT '地址',
  `longitude` DECIMAL(10,6) NULL COMMENT '经度',
  `latitude` DECIMAL(10,6) NULL COMMENT '纬度',
  `category` VARCHAR(64) NULL COMMENT '地点类型',
  `typecode` VARCHAR(20) NULL COMMENT '高德类型码',
  `note` VARCHAR(500) NULL COMMENT '用户备注',
  `source` VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT 'TRIP/SEARCH/MANUAL',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_place` (`user_id`, `place_key`),
  KEY `idx_user_deleted_time` (`user_id`, `deleted`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户收藏景点';

-- ============================================================
-- 打车订单表
-- ============================================================
CREATE TABLE IF NOT EXISTS `taxi_order` (
  `id` BIGINT NOT NULL COMMENT '订单ID（雪花）',
  `order_no` VARCHAR(32) NOT NULL COMMENT '业务订单号',
  `user_id` BIGINT NOT NULL COMMENT '下单用户',
  `status` VARCHAR(20) NOT NULL COMMENT 'CREATED/COMPLETED/CANCELLED',
  `pickup_name` VARCHAR(128) NOT NULL COMMENT '起点名称',
  `pickup_address` VARCHAR(255) NULL COMMENT '起点地址',
  `pickup_longitude` DECIMAL(10,6) NOT NULL COMMENT '起点经度',
  `pickup_latitude` DECIMAL(10,6) NOT NULL COMMENT '起点纬度',
  `destination_name` VARCHAR(128) NOT NULL COMMENT '终点名称',
  `destination_address` VARCHAR(255) NULL COMMENT '终点地址',
  `destination_longitude` DECIMAL(10,6) NOT NULL COMMENT '终点经度',
  `destination_latitude` DECIMAL(10,6) NOT NULL COMMENT '终点纬度',
  `city` VARCHAR(64) NULL COMMENT '城市',
  `vehicle_type` VARCHAR(64) NULL COMMENT '车型名称',
  `vehicle_category` VARCHAR(32) NULL COMMENT '车型品类码',
  `distance_m` INT NULL COMMENT '距离（米）',
  `duration_min` INT NULL COMMENT '预计时长（分钟）',
  `estimated_fare` DECIMAL(10,2) NULL COMMENT '预估车费',
  `coupon_fare` DECIMAL(10,2) NULL COMMENT '券后价',
  `currency` CHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `provider` VARCHAR(20) NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK/DIDI_MCP',
  `provider_estimate_id` VARCHAR(128) NULL COMMENT '外部预估/链路ID',
  `jump_url` VARCHAR(2048) NULL COMMENT '滴滴跳转链接',
  `jump_links` JSON NULL COMMENT '多端跳转链接',
  `request_id` VARCHAR(64) NULL COMMENT '创建请求链路ID',
  `idempotency_key` VARCHAR(64) NULL COMMENT '创建幂等键',
  `cancel_reason` VARCHAR(255) NULL COMMENT '取消原因',
  `cancelled_time` DATETIME NULL COMMENT '取消时间',
  `completed_time` DATETIME NULL COMMENT '完成时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_user_idempotency` (`user_id`, `idempotency_key`),
  KEY `idx_user_status_time` (`user_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模拟打车订单';
