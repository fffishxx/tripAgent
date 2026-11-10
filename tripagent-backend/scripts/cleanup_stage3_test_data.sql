-- 清理阶段三验收脚本使用的测试用户及其业务数据。
-- 仅删除手机号 19900000001 / 19900000002 的记录。

USE `tripagent`;

SET @ua := (SELECT id FROM `user` WHERE phone = '19900000001');
SET @ub := (SELECT id FROM `user` WHERE phone = '19900000002');

DELETE FROM `trip_item`
WHERE trip_id IN (SELECT id FROM `trip` WHERE user_id IN (@ua, @ub));

DELETE FROM `trip_day`
WHERE trip_id IN (SELECT id FROM `trip` WHERE user_id IN (@ua, @ub));

DELETE FROM `trip`
WHERE user_id IN (@ua, @ub);

DELETE FROM `favorite_place`
WHERE user_id IN (@ua, @ub);

DELETE FROM `taxi_order`
WHERE user_id IN (@ua, @ub);

DELETE FROM `user_preference`
WHERE user_id IN (@ua, @ub);

DELETE FROM `user`
WHERE id IN (@ua, @ub);
