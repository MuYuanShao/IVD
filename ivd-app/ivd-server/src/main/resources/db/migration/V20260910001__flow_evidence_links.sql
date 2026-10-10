ALTER TABLE consumption_record
    ADD COLUMN batch_id BIGINT NULL COMMENT '实际操作的库存批次ID',
    ADD COLUMN purchase_order_item_id BIGINT NULL COMMENT '验收采购明细ID',
    ADD COLUMN requisition_item_id BIGINT NULL COMMENT '拣货申领明细ID',
    ADD INDEX idx_consumption_batch_id (batch_id);
ALTER TABLE device_batch_load
    ADD COLUMN batch_id BIGINT NULL COMMENT '实际库存批次ID，历史未记录时为空',
    ADD INDEX idx_device_load_batch (device_id,batch_id,unload_time);
ALTER TABLE purchase_requisition ADD COLUMN received_at DATETIME(3) NULL COMMENT '实际科室签收时间';
ALTER TABLE settlement_record
    ADD COLUMN source_item_kind VARCHAR(32) NOT NULL DEFAULT 'LEGACY' COMMENT '来源键类型：历史/发货流水',
    DROP INDEX uk_settlement_source_item,
    ADD UNIQUE INDEX uk_settlement_source_item (settlement_type,source_id,source_item_kind,source_item_id);
