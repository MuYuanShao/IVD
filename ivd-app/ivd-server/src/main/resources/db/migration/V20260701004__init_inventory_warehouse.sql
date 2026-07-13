-- ============================================
-- 脚本: V20260701004__init_inventory_warehouse.sql
-- 说明: M06 三级库 + 批次 + 设备 + 上机记录
-- 模块: M06/M08/M10
-- 依赖: V20260701001
-- ============================================

-- 仓库表（三级库自引用树）
CREATE TABLE warehouse (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(64) NOT NULL COMMENT '库房名称',
    level TINYINT NOT NULL COMMENT '层级: 1=中心库, 2=科室库, 3=组库',
    parent_id BIGINT COMMENT '父仓库ID',
    location_code VARCHAR(20) COMMENT '库位编码（库区-货架）',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_parent (parent_id),
    INDEX idx_level (level),
    FOREIGN KEY (parent_id) REFERENCES warehouse(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='三级库仓库表';

-- 设备注册表
CREATE TABLE device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    device_code VARCHAR(64) NOT NULL COMMENT '设备资产码',
    name VARCHAR(128) NOT NULL COMMENT '设备名称',
    model VARCHAR(64) COMMENT '型号',
    manufacturer VARCHAR(128) COMMENT '厂家',
    location_id BIGINT COMMENT '所在库房ID',
    last_calibration_date DATE COMMENT '上次校准日期',
    next_calibration_date DATE COMMENT '下次校准日期（校准到期<7天触发R-BATCH-004）',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否在用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE INDEX idx_device_code (device_code),
    INDEX idx_location (location_id),
    FOREIGN KEY (location_id) REFERENCES warehouse(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验设备注册表';

-- ★ 批次表（核心生命周期实体）
CREATE TABLE reagent_batch (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    batch_no VARCHAR(64) NOT NULL COMMENT '批次号（UDI-PI或虚拟批号BATCH-CONS-...）',
    reagent_id BIGINT NOT NULL COMMENT '试剂ID',
    material_type ENUM('REAGENT','CALIBRATOR','QC_MATERIAL','CONSUMABLE','OTHER') NOT NULL COMMENT '物料类型',
    udi_di VARCHAR(128) COMMENT 'UDI-DI',
    udi_pi VARCHAR(128) COMMENT 'UDI-PI（生产批号）',
    manufacture_date DATE COMMENT '生产日期',
    expiry_date DATE COMMENT '效期至',
    open_date DATE COMMENT '开瓶日期',
    open_expiry_date DATE COMMENT '开瓶效期（open_date + stability_days_after_open）',
    stock DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '当前库存量',
    unit VARCHAR(20) COMMENT '单位',
    unit_price DECIMAL(12,2) COMMENT '入库单价',
    avg_cost DECIMAL(12,2) COMMENT '移动加权平均成本',
    udi_status ENUM('RECEIVED','IN_STOCK','PENDING_UDI','TRANSFERRED','IN_USE','CONSUMED','RETURNED','EXPIRED','RECALLED','BACK_SUPPLIER','SCRAPPED','DESTROYED')
        NOT NULL DEFAULT 'RECEIVED' COMMENT 'UDI状态',
    warehouse_id BIGINT COMMENT '所在仓库ID',
    location_code VARCHAR(20) COMMENT '库位编码',
    fefo_priority INT DEFAULT 999 COMMENT 'FEFO优先级（越小越优先出库）',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE INDEX idx_batch_no (batch_no),
    INDEX idx_reagent_id (reagent_id),
    INDEX idx_udi_status (udi_status),
    INDEX idx_warehouse (warehouse_id),
    INDEX idx_expiry (expiry_date),
    INDEX idx_fefo (warehouse_id, fefo_priority, expiry_date),
    FOREIGN KEY (reagent_id) REFERENCES reagent_catalog(id),
    FOREIGN KEY (warehouse_id) REFERENCES warehouse(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次表（核心生命周期实体）';

-- ★ 设备-批次上机记录（自动审核命脉）
CREATE TABLE device_batch_load (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    batch_no VARCHAR(64) NOT NULL COMMENT '批次号',
    reagent_id BIGINT NOT NULL COMMENT '试剂ID',
    load_time DATETIME(3) NOT NULL COMMENT '上机时间',
    unload_time DATETIME(3) COMMENT '退机时间',
    operator_id INT NOT NULL COMMENT '操作人ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_device_batch (device_id, load_time, unload_time),
    INDEX idx_batch_no (batch_no),
    INDEX idx_load_time (load_time),
    FOREIGN KEY (device_id) REFERENCES device(id),
    FOREIGN KEY (reagent_id) REFERENCES reagent_catalog(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备批次上机记录（自动审核数据基础）';
