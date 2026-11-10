-- 阶段六集成测试数据清理（MySQL: tripagent）
-- 覆盖 Java API 集成测试固定测试手机号及 Python 跨服务测试手机号。
-- 用法：mysql -u root -p tripagent < scripts/cleanup_stage6_test_data.sql

SET @phones = '19900000101,19900000102,19900000103,19900000104,19900000105';

SET @ids = (
  SELECT GROUP_CONCAT(`id`)
  FROM `user`
  WHERE `phone` IN (
    '19900000101', '19900000102', '19900000103', '19900000104', '19900000105'
  )
);

DELETE FROM `trip_item` WHERE FIND_IN_SET(`trip_id`, @ids);
DELETE FROM `trip_day` WHERE FIND_IN_SET(`trip_id`, @ids);
DELETE FROM `trip` WHERE FIND_IN_SET(`user_id`, @ids);
DELETE FROM `favorite_place` WHERE FIND_IN_SET(`user_id`, @ids);
DELETE FROM `taxi_order` WHERE FIND_IN_SET(`user_id`, @ids);
DELETE FROM `user_preference` WHERE FIND_IN_SET(`user_id`, @ids);
DELETE FROM `user` WHERE FIND_IN_SET(`id`, @ids);

-- 可选：如果与 agent_center 使用同一个 MySQL 实例，清理测试会话元数据
-- DELETE FROM `agent_center`.`chat_session` WHERE FIND_IN_SET(`user_id`, @ids);
