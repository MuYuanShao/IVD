-- ============================================
-- 脚本: V20260701001__init_reagent_catalog.sql
-- 说明: M01 试剂目录主表 + 五码映射 + 审批日志 + UDI待补全追踪
-- 模块: M01 试剂目录管理
-- 依赖: 无（一期建表基线）
-- ============================================

-- 试剂目录主表
CREATE TABLE reagent_catalog (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    his_code VARCHAR(50) NOT NULL COMMENT '院内编码（IVD-{组别缩写}-{序号}）',
    generic_name VARCHAR(200) NOT NULL COMMENT '物料名称',
    material_type ENUM('REAGENT','CALIBRATOR','QC_MATERIAL','CONSUMABLE','OTHER')
        NOT NULL DEFAULT 'REAGENT' COMMENT '物料类型·决定全流程行为',
    manufacturer VARCHAR(200) COMMENT '生产厂家',
    specification VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位（盒/瓶/箱/包）',
    storage_condition VARCHAR(100) COMMENT '储存条件',
    test_groups VARCHAR(200) COMMENT '适用组别（多选，逗号分隔）',
    udi_di VARCHAR(128) COMMENT 'UDI-DI（按material_type决定必填性）',
    gtin VARCHAR(14) COMMENT 'GTIN',
    insurance_code VARCHAR(27) COMMENT '医保编码',
    registration_cert_no VARCHAR(128) COMMENT 'NMPA注册证号',
    detection_method VARCHAR(100) COMMENT '检测方法学',
    tests_per_kit INT COMMENT '标称测试数（仅REAGENT必填）',
    price_per_unit DECIMAL(12,2) COMMENT '单价（元），含税',
    stability_days_after_open INT COMMENT '开瓶稳定天数（仅REAGENT填写）',
    min_stock_level DECIMAL(10,2) DEFAULT 0 COMMENT '最低库存警戒',
    max_stock_level DECIMAL(10,2) DEFAULT 99999 COMMENT '最高库存',
    lead_time_days INT NOT NULL DEFAULT 7 COMMENT '采购提前期(天)',
    device_bind_mode ENUM('FORCE','FLEXIBLE') NOT NULL DEFAULT 'FORCE' COMMENT '设备关联策略',
    approval_status ENUM('DRAFT','PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE INDEX idx_his_code (his_code),
    INDEX idx_material_type (material_type),
    INDEX idx_approval_status (approval_status),
    INDEX idx_generic_name (generic_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试剂目录主表';

-- 五码映射表
CREATE TABLE five_code_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    reagent_id BIGINT NOT NULL COMMENT '试剂ID',
    udi_di VARCHAR(128) NOT NULL COMMENT 'UDI-DI（主键码）',
    gtin VARCHAR(14) COMMENT 'GTIN',
    insurance_code VARCHAR(27) COMMENT '医保编码',
    nmpa_reg_no VARCHAR(128) COMMENT 'NMPA注册证编号',
    his_code VARCHAR(50) NOT NULL COMMENT '院内HIS编码',
    mapping_status ENUM('MAPPED','PENDING_MAPPING','INSURANCE_PENDING')
        NOT NULL DEFAULT 'PENDING_MAPPING' COMMENT '映射状态',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE INDEX idx_reagent_id (reagent_id),
    INDEX idx_udi_di (udi_di),
    INDEX idx_mapping_status (mapping_status),
    FOREIGN KEY (reagent_id) REFERENCES reagent_catalog(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='五码映射表';

-- 试剂目录审批日志
CREATE TABLE catalog_approval_log (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    reagent_id BIGINT NOT NULL COMMENT '关联试剂ID',
    change_type ENUM('CREATE','UPDATE','DELETE','DISABLE') NOT NULL COMMENT '变更类型',
    field_changes JSON COMMENT '变更字段old→new映射',
    change_reason TEXT COMMENT '申请人填写的变更原因',
    submitter_id BIGINT NOT NULL COMMENT '申请人ID',
    submitter_name VARCHAR(64) NOT NULL COMMENT '申请人姓名',
    reviewer_id BIGINT COMMENT '审批人ID',
    reviewer_name VARCHAR(64) COMMENT '审批人姓名',
    review_status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '审批状态',
    review_comment TEXT COMMENT '审批意见（驳回时必填）',
    submitted_at DATETIME(3) NOT NULL COMMENT '提交时间',
    reviewed_at DATETIME(3) COMMENT '审批时间',
    INDEX idx_reagent_id (reagent_id),
    INDEX idx_review_status (review_status, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试剂目录审批日志';

-- UDI 待补全追踪表
CREATE TABLE udi_backlog (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    reagent_id BIGINT NOT NULL COMMENT '试剂ID',
    material_type ENUM('REAGENT','CALIBRATOR','QC_MATERIAL') NOT NULL COMMENT '物料类型',
    impact_level ENUM('BLOCK','WARN') NOT NULL DEFAULT 'WARN' COMMENT '影响等级',
    has_open_po TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否存在关联的未完成PO',
    days_pending INT DEFAULT 0 COMMENT '待补全天数',
    notified_at DATETIME(3) COMMENT '最后通知时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE INDEX idx_reagent_id (reagent_id),
    INDEX idx_impact_level (impact_level),
    FOREIGN KEY (reagent_id) REFERENCES reagent_catalog(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UDI待补全追踪表';
