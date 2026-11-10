-- 清理阶段五验收脚本使用的测试用户及其业务数据。
-- 仅删除手机号 19900000005 的记录。

USE `tripagent`;

SET @u := (SELECT id FROM `user` WHERE phone = '19900000005');

DELETE FROM `trip_item`
WHERE trip_id IN (SELECT id FROM `trip` WHERE user_id = @u);

DELETE FROM `trip_day`
WHERE trip_id IN (SELECT id FROM `trip` WHERE user_id = @u);

DELETE FROM `trip`
WHERE user_id = @u;

DELETE FROM `favorite_place`
WHERE user_id = @u;

DELETE FROM `taxi_order`
WHERE user_id = @u;

DELETE FROM `user_preference`
WHERE user_id = @u;

DELETE FROM `user`
WHERE id = @u;
