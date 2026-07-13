-- 为 reagent_batch 表添加乐观锁版本号字段
ALTER TABLE reagent_batch ADD COLUMN version INT DEFAULT 0 NOT NULL COMMENT '乐观锁版本号';
