-- ============================================
-- 脚本: V20260701005__init_consumption_settlement.sql
-- 说明: M08 消耗记录 + X2 结算记录
-- 模块: M08/M09/X2
-- 依赖: V20260701004
-- ============================================

-- 消耗记录表（含验收入库 + 领用消耗两种类型）
CREATE TABLE consumption_record (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    record_type ENUM('INBOUND','CONSUME') NOT NULL COMMENT '记录类型',
    batch_no VARCHAR(64) COMMENT '批次号',
    reagent_id BIGINT NOT NULL COMMENT '试剂ID',
    material_type ENUM('REAGENT','CALIBRATOR','QC_MATERIAL','CONSUMABLE','OTHER') NOT NULL COMMENT '物料类型',
    device_id BIGINT COMMENT '关联设备ID（CONSUMABLE/OTHER时为NULL）',
    quantity DECIMAL(10,2) NOT NULL COMMENT '数量',
    unit VARCHAR(20) COMMENT '单位',
    unit_price DECIMAL(12,2) COMMENT '单价',
    amount DECIMAL(12,2) COMMENT '金额',
    purpose ENUM('ROUTINE','BATCH_TEST','EQUIP_MAINT','DEPT_USE','OTHER') COMMENT '消耗用途',
    warehouse_id BIGINT COMMENT '仓库ID',
    operator_id INT NOT NULL COMMENT '操作人ID',
    remark VARCHAR(500) COMMENT '备注',
    occurred_at DATETIME(3) NOT NULL COMMENT '发生时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_batch_no (batch_no),
    INDEX idx_reagent (reagent_id),
    INDEX idx_warehouse (warehouse_id),
    INDEX idx_occurred_at (occurred_at),
    INDEX idx_record_type (record_type),
    FOREIGN KEY (reagent_id) REFERENCES reagent_catalog(id),
    FOREIGN KEY (device_id) REFERENCES device(id),
    FOREIGN KEY (warehouse_id) REFERENCES warehouse(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消耗记录表（含验收入库+领用消耗）';

-- 结算记录表（X2）
CREATE TABLE settlement_record (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    settlement_no VARCHAR(32) NOT NULL COMMENT '结算单号 STL-YYYYMMDD-NNNN',
    settlement_type ENUM('REQUISITION','CONSUMPTION') NOT NULL COMMENT '结算类型',
    source_id BIGINT NOT NULL COMMENT '源单据ID（申领单ID/消耗记录ID）',
    source_no VARCHAR(64) COMMENT '源单据号',
    batch_no VARCHAR(64) COMMENT '批次号',
    reagent_id BIGINT NOT NULL COMMENT '试剂ID',
    quantity DECIMAL(10,2) NOT NULL COMMENT '结算数量',
    unit_price DECIMAL(12,2) COMMENT '结算单价',
    amount DECIMAL(12,2) NOT NULL COMMENT '结算金额',
    status ENUM('ACTIVE','REVERSED') NOT NULL DEFAULT 'ACTIVE' COMMENT '结算记录状态',
    from_dept_id INT COMMENT '来源科室ID',
    settled_by INT COMMENT '结算执行人/系统ID',
    settled_at DATETIME(3) NOT NULL COMMENT '结算时间',
    reversed_at DATETIME(3) COMMENT '冲销时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE INDEX idx_settlement_no (settlement_no),
    INDEX idx_source (settlement_type, source_id),
    INDEX idx_reagent (reagent_id),
    INDEX idx_settled_at (settled_at),
    INDEX idx_status (status),
    FOREIGN KEY (reagent_id) REFERENCES reagent_catalog(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算记录表（X2第一层结算）';

-- 每日进销存快照
CREATE TABLE daily_inventory_snapshot (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    snapshot_date DATE NOT NULL COMMENT '快照日期',
    reagent_id BIGINT NOT NULL COMMENT '试剂ID',
    warehouse_id BIGINT COMMENT '仓库ID',
    opening DECIMAL(12,2) COMMENT '期初',
    inbound DECIMAL(12,2) DEFAULT 0 COMMENT '入库',
    outbound DECIMAL(12,2) DEFAULT 0 COMMENT '出库（消耗+调拨+退供+报损）',
    adjustment DECIMAL(12,2) DEFAULT 0 COMMENT '盘盈亏调整',
    closing DECIMAL(12,2) COMMENT '期末',
    difference DECIMAL(12,2) COMMENT '差异 = closing - (opening + inbound - outbound + adjustment)',
    difference_ratio DECIMAL(6,4) COMMENT '差异率',
    is_balanced TINYINT(1) DEFAULT 1 COMMENT '是否平衡（差异率<1%）',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE INDEX idx_snapshot (snapshot_date, reagent_id, warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日进销存快照';
