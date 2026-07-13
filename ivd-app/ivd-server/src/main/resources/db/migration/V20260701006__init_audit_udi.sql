-- ============================================
-- 脚本: V20260701006__init_audit_udi.sql
-- 说明: S2/S3 审核规则 + 决策日志 + X1 UDI状态日志
-- 模块: S2/S3/X1
-- 依赖: V20260701004
-- ============================================

-- 审核规则表
CREATE TABLE audit_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    rule_id VARCHAR(32) NOT NULL COMMENT '规则编号（如R-BATCH-003）',
    name VARCHAR(128) NOT NULL COMMENT '规则名称',
    layer ENUM('RESULT','BATCH','LINKAGE') NOT NULL COMMENT '规则层级',
    priority INT NOT NULL DEFAULT 50 COMMENT '优先级（越大越优先）',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    scope JSON COMMENT '适用范围 {"test_items":["*"],"test_groups":["IMMUNO","BIOCHEM"]}',
    rule_condition JSON COMMENT '触发条件 {"type":"BATCH_STATUS","operator":"IN","value":["OUT_OF_CONTROL"]}',
    action ENUM('AUTO_RELEASE','WARN_RELEASE','HOLD_REVIEW','REJECT_RETEST') NOT NULL COMMENT '决策动作',
    reason_template VARCHAR(500) COMMENT '原因模板（支持占位符 {batch_no} {batch_status}）',
    link_to VARCHAR(128) COMMENT '联动目标（如 qc_event_id）',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE INDEX idx_rule_id (rule_id),
    INDEX idx_layer (layer),
    INDEX idx_enabled_priority (enabled, priority DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核规则定义表';

-- 一期内置规则初始化
INSERT INTO audit_rule (rule_id, name, layer, priority, action, reason_template) VALUES
('R-RST-001', '结果超参考范围1.5倍', 'RESULT', 50, 'WARN_RELEASE', '结果值超参考范围上限1.5倍'),
('R-RST-002', '危急值', 'RESULT', 90, 'HOLD_REVIEW', '结果触及危急值范围'),
('R-RST-003', 'Delta Check超限', 'RESULT', 70, 'HOLD_REVIEW', '与上次结果差值大于50%'),
('R-BATCH-001', '试剂效期不足7天', 'BATCH', 30, 'WARN_RELEASE', '试剂批次 {batch_no} 效期不足7天({expiry_date})'),
('R-BATCH-002', '试剂开瓶超稳定期', 'BATCH', 70, 'HOLD_REVIEW', '试剂批次 {batch_no} 开瓶超稳定期({open_expiry_date})'),
('R-BATCH-003', '批次被人工标记异常', 'BATCH', 80, 'HOLD_REVIEW', '试剂批次 {batch_no} 状态异常({batch_status})，结果需人工复核'),
('R-BATCH-004', '设备距下次校准不足7天', 'BATCH', 60, 'HOLD_REVIEW', '设备 {device_code} 距下次校准不足7天({next_calibration_date})'),
('R-LNK-001', '试剂证照过期', 'LINKAGE', 95, 'REJECT_RETEST', '试剂 {reagent_name} 证照已过期，相关结果拒绝'),
('R-LNK-002', '批次库存被冻结', 'LINKAGE', 80, 'HOLD_REVIEW', '批次 {batch_no} 库存已被冻结，结果需人工复核');

-- 审核决策日志表
CREATE TABLE audit_decision_log (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    observation_id VARCHAR(64) NOT NULL COMMENT 'LIS结果ID',
    patient_id_hash VARCHAR(64) NOT NULL COMMENT '患者ID哈希',
    test_item_code VARCHAR(32) NOT NULL COMMENT '检验项目编码',
    test_value DECIMAL(12,4) COMMENT '检验值',
    device_id VARCHAR(64) COMMENT '设备ID',
    test_datetime DATETIME(3) COMMENT '检验时间',
    batch_no VARCHAR(64) COMMENT '关联批次号',
    decision ENUM('AUTO_RELEASE','WARN_RELEASE','HOLD_REVIEW','REJECT_RETEST') NOT NULL COMMENT '系统决策',
    triggered_rules JSON COMMENT '触发的规则列表',
    audit_latency_ms INT COMMENT '审核耗时(ms)',
    manual_decision ENUM('RELEASED','HELD','RETEST') COMMENT '人工复核决策',
    auditor_id INT COMMENT '审核人ID',
    auditor_note TEXT COMMENT '审核备注',
    recheck_value DECIMAL(12,4) COMMENT '复测值',
    decided_at DATETIME(3) NOT NULL COMMENT '决策时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_observation (observation_id),
    INDEX idx_batch_no (batch_no),
    INDEX idx_decision (decision),
    INDEX idx_decided_at (decided_at, decision),
    INDEX idx_patient_hash (patient_id_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核决策日志表';

-- ★ UDI 状态流转日志（不可变，APPEND-ONLY）
CREATE TABLE udi_status_log (
    id BIGINT NOT NULL COMMENT '主键（雪花ID）',
    batch_no VARCHAR(64) NOT NULL COMMENT '批次号',
    from_status VARCHAR(32) COMMENT '转移前状态',
    to_status VARCHAR(32) NOT NULL COMMENT '转移后状态',
    operator_id INT COMMENT '操作人ID（系统操作为NULL）',
    operator_name VARCHAR(64) COMMENT '操作人姓名',
    remark VARCHAR(500) COMMENT '备注',
    occurred_at DATETIME(3) NOT NULL COMMENT '转移发生时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_batch_no (batch_no, occurred_at),
    INDEX idx_occurred_at (occurred_at),
    PRIMARY KEY (id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UDI状态流转日志（不可变，APPEND-ONLY）'
/*!50500 PARTITION BY RANGE COLUMNS(occurred_at)
(PARTITION p202601 VALUES LESS THAN ('2026-02-01'),
 PARTITION p202602 VALUES LESS THAN ('2026-03-01'),
 PARTITION p202603 VALUES LESS THAN ('2026-04-01'),
 PARTITION p202604 VALUES LESS THAN ('2026-05-01'),
 PARTITION p202605 VALUES LESS THAN ('2026-06-01'),
 PARTITION p202606 VALUES LESS THAN ('2026-07-01'),
 PARTITION p202607 VALUES LESS THAN ('2026-08-01'),
 PARTITION p202608 VALUES LESS THAN ('2026-09-01'),
 PARTITION p202609 VALUES LESS THAN ('2026-10-01'),
 PARTITION p202610 VALUES LESS THAN ('2026-11-01'),
 PARTITION p202611 VALUES LESS THAN ('2026-12-01'),
 PARTITION p202612 VALUES LESS THAN ('2027-01-01'),
 PARTITION p_future VALUES LESS THAN MAXVALUE) */;
