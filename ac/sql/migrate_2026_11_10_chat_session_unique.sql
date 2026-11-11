-- 2026-11-10 C-06：chat_session.session_id 唯一约束
-- 适用：已有 agent_center 库；新库直接使用 agent_center.sql。
-- 执行前先确认没有重复 session_id；若有重复，先人工合并/清理。

USE `agent_center`;

-- 1) 检查重复（应返回 0 行）
SELECT session_id, COUNT(*) AS cnt
FROM chat_session
GROUP BY session_id
HAVING COUNT(*) > 1;

-- 2) 将普通索引替换为唯一索引
--    如果旧索引名不同，请先确认 SHOW INDEX FROM chat_session;
ALTER TABLE `chat_session`
  DROP INDEX `session_id_index`,
  ADD UNIQUE KEY `uk_session_id` (`session_id`) USING BTREE;
