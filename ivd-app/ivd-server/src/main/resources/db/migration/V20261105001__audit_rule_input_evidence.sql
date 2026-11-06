ALTER TABLE audit_decision_log
    ADD COLUMN test_value_text VARCHAR(1000) NULL COMMENT '实际参与规则判定的结果输入；历史未记录时为空';
