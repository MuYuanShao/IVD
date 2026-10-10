-- 结算来源明细键：应用层预检改善提示，唯一索引负责并发场景最终防重。
ALTER TABLE settlement_record
    ADD COLUMN source_item_id BIGINT NULL AFTER source_id;

UPDATE settlement_record sr
LEFT JOIN purchase_requisition_item pri
    ON sr.settlement_type = 'REQUISITION'
    AND pri.req_id = sr.source_id
    AND pri.reagent_id = sr.reagent_id
SET sr.source_item_id = CASE
    WHEN sr.settlement_type = 'REQUISITION' THEN COALESCE(pri.id, sr.id)
    ELSE COALESCE(sr.source_id, sr.id)
END;

ALTER TABLE settlement_record
    MODIFY COLUMN source_item_id BIGINT NOT NULL COMMENT '源单据明细ID',
    ADD UNIQUE INDEX uk_settlement_source_item (settlement_type, source_id, source_item_id);
