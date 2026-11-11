-- 创建数据库
CREATE DATABASE IF NOT EXISTS agent_center
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_bin;

USE `agent_center`;


-- 创建应用信息表
CREATE TABLE IF NOT EXISTS `app_info` (
	`id` BIGINT NOT NULL COMMENT '数据id',
	`app_key` VARCHAR(64) NOT NULL COMMENT '应用key' COLLATE 'utf8mb4_bin',
	`app_secret` VARCHAR(64) NOT NULL COMMENT '应用秘钥' COLLATE 'utf8mb4_bin',
	`name` VARCHAR(32) NULL DEFAULT NULL COMMENT '应用名称' COLLATE 'utf8mb4_bin',
	`create_time` DATETIME NOT NULL DEFAULT (now()) COMMENT '创建时间',
	`update_time` DATETIME NOT NULL DEFAULT (now()) ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
	PRIMARY KEY (`id`) USING BTREE,
	INDEX `app_key` (`app_key`) USING BTREE
)
COMMENT='应用信息表'
COLLATE='utf8mb4_bin'
ENGINE=InnoDB
;


-- 插入示例应用身份；生产必须替换为 .env 中的 AGENT_APP_KEY/AGENT_APP_SECRET。
-- 新库首次初始化后，请同步更新 app_info，或使用部署脚本写入。
INSERT IGNORE INTO `app_info` (`id`, `app_key`, `app_secret`, `name`, `create_time`, `update_time`) VALUES (7388495056530878465, 'change-me-app-key', 'change-me-app-secret', '天机AI助手', '2025-10-27 16:43:27', '2025-10-27 16:43:34');


-- 创建对话session表
CREATE TABLE IF NOT EXISTS `chat_session` (
	`id` BIGINT NOT NULL COMMENT '数据id',
	`session_id` VARCHAR(32) NOT NULL COMMENT '会话id' COLLATE 'utf8mb4_bin',
	`user_id` BIGINT NOT NULL DEFAULT '0' COMMENT '用户id',
	`agent_id` BIGINT NOT NULL COMMENT '智能体id',
	`title` VARCHAR(100) NULL DEFAULT NULL COMMENT '会话标题' COLLATE 'utf8mb4_bin',
	`create_time` DATETIME NOT NULL DEFAULT (now()) COMMENT '创建时间',
	`update_time` DATETIME NOT NULL DEFAULT (now()) ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
	PRIMARY KEY (`id`) USING BTREE,
	UNIQUE KEY `uk_session_id` (`session_id`) USING BTREE,
	INDEX `user_id_index` (`user_id`) USING BTREE,
	INDEX `update_time_index` (`update_time`) USING BTREE,
	INDEX `agent_id_index` (`agent_id`) USING BTREE
)
COMMENT='对话session'
COLLATE='utf8mb4_bin'
ENGINE=InnoDB
;
