ALTER TABLE udi_status_log
    ADD COLUMN batch_id BIGINT NULL COMMENT '确切批次ID；历史未记录时为空',
    ADD INDEX idx_udi_status_institution_batch (institution_id, batch_id, occurred_at);
