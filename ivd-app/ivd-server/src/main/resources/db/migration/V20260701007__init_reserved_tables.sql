-- ============================================
-- 脚本: V20260701007__init_reserved_tables.sql
-- 说明: 二期预留表（一期建空表，二期填充数据）
-- 模块: M11/M14/M15（二期）
-- 依赖: 无
-- ============================================

-- TODO: Phase 2 — 质控结果表（二期填充，一期建空表）
CREATE TABLE qc_result (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    qc_material_id BIGINT COMMENT '质控品ID',
    batch_no VARCHAR(64) COMMENT '质控品批号',
    test_item VARCHAR(32) COMMENT '检测项目',
    level TINYINT COMMENT '质控水平（1/2/3）',
    target_value DECIMAL(12,4) COMMENT '靶值',
    measured_value DECIMAL(12,4) COMMENT '实测值',
    sd DECIMAL(8,4) COMMENT '标准差',
    westgard_rule_violations JSON COMMENT 'Westgard规则违规列表',
    result ENUM('IN_CONTROL','WARNING','OUT_OF_CONTROL') COMMENT '质控结果',
    device_id BIGINT COMMENT '设备ID',
    tested_at DATETIME(3) COMMENT '检测时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_batch_no (batch_no),
    INDEX idx_tested_at (tested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质控结果表 — TODO: Phase 2';

-- TODO: Phase 2 — POCT设备表（二期填充）
CREATE TABLE poct_device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    device_code VARCHAR(64) COMMENT 'POCT设备编码',
    name VARCHAR(128) COMMENT '设备名称',
    model VARCHAR(64) COMMENT '型号',
    location VARCHAR(128) COMMENT '所在位置（科室/病房）',
    is_active TINYINT(1) DEFAULT 1 COMMENT '是否在用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE INDEX idx_device_code (device_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='POCT设备表 — TODO: Phase 2';

-- TODO: Phase 3 — AI调用日志表（三期填充）
CREATE TABLE ai_consultation_log (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    model VARCHAR(64) COMMENT '模型名称',
    prompt_hash VARCHAR(64) COMMENT 'Prompt哈希',
    response TEXT COMMENT 'AI响应',
    tokens_in INT COMMENT '输入Token数',
    tokens_out INT COMMENT '输出Token数',
    latency_ms INT COMMENT '响应延迟(ms)',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用日志表 — TODO: Phase 3';
