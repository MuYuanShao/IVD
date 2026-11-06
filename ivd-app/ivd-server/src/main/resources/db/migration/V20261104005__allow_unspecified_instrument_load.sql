ALTER TABLE device_batch_load
    MODIFY COLUMN device_id BIGINT NULL COMMENT '设备ID，柔性关联上机可未指定';
