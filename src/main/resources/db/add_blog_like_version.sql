-- 对已有数据库执行一次；新数据库导入 hmdp.sql 后也需执行本脚本。
-- 切换前停止旧版点赞写入，避免旧服务与异步快照同时修改计数。
ALTER TABLE tb_blog
ADD COLUMN like_version BIGINT NOT NULL DEFAULT 0 COMMENT '点赞同步版本';
