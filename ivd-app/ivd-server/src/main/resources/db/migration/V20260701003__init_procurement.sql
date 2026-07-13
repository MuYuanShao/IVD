-- ============================================
-- 脚本: V20260701003__init_procurement.sql
-- 说明: M04 采购管理 — 申领单 + 缺货单 + 采购订单 + 明细
-- 模块: M04 采购管理
-- 依赖: V20260701001, V20260701002
-- ============================================

-- 申领单主表（二级库 → 中心库）
CREATE TABLE purchase_requisition (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    req_no VARCHAR(32) NOT NULL COMMENT '申领单号 REQ-YYYYMMDD-NNN',
    from_dept_id INT NOT NULL COMMENT '申领科室ID（二级库）',
    to_warehouse_id INT NOT NULL DEFAULT 1 COMMENT '目标仓库ID（中心库，默认1）',
    applicant_id INT NOT NULL COMMENT '申请人ID',
    urgency ENUM('NORMAL','URGENT','CRITICAL') DEFAULT 'NORMAL' COMMENT '紧急程度',
    status ENUM('DRAFT','PENDING_REVIEW','REVIEWED','PICKING','PARTIAL_FULFILLED','FULFILLED','SHORTAGE_PENDING','CANCELLED')
        DEFAULT 'DRAFT' COMMENT '申领状态',
    reviewer_id INT COMMENT '中心库审核人ID',
    review_time DATETIME(3) COMMENT '审核时间',
    review_comment VARCHAR(500) COMMENT '审核意见',
    remark VARCHAR(1000) COMMENT '备注',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE INDEX idx_req_no (req_no),
    INDEX idx_status (status),
    INDEX idx_from_dept (from_dept_id),
    INDEX idx_applicant (applicant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申领单主表（二级库→中心库）';

-- 申领明细表
CREATE TABLE purchase_requisition_item (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    req_id BIGINT NOT NULL COMMENT '申领单ID',
    line_no INT NOT NULL COMMENT '行号',
    reagent_id INT NOT NULL COMMENT '试剂ID (FK → M01)',
    specification VARCHAR(100) COMMENT '规格（自动填充）',
    qty_requested DECIMAL(10,2) NOT NULL COMMENT '申领数量',
    stock_on_hand DECIMAL(10,2) DEFAULT 0 COMMENT '中心库当前库存（审核时实查值）',
    qty_fulfilled DECIMAL(10,2) DEFAULT 0 COMMENT '实发数量',
    qty_shortage DECIMAL(10,2) DEFAULT 0 COMMENT '缺货数量',
    unit_price DECIMAL(10,2) COMMENT '预估单价',
    amount DECIMAL(12,2) COMMENT '行金额',
    group_id INT COMMENT '检测组别ID',
    shortage_order_id BIGINT COMMENT '关联缺货单ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_req_id (req_id),
    INDEX idx_reagent (reagent_id),
    INDEX idx_shortage_order (shortage_order_id),
    FOREIGN KEY (req_id) REFERENCES purchase_requisition(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申领明细表（含拣货结果）';

-- 缺货单主表
CREATE TABLE shortage_order (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    shortage_no VARCHAR(32) NOT NULL COMMENT '缺货单号 SH-YYYYMMDD-NNN',
    req_id BIGINT NOT NULL COMMENT '来源申领单ID',
    from_dept_id INT NOT NULL COMMENT '申领科室ID',
    status ENUM('PENDING_PROCUREMENT','PURCHASING','REPLENISHED','COMPLETED','CANCELLED')
        DEFAULT 'PENDING_PROCUREMENT' COMMENT '缺货状态',
    urgency ENUM('NORMAL','URGENT','CRITICAL') DEFAULT 'NORMAL',
    suggested_supplier_id INT COMMENT '建议供应商ID',
    po_id BIGINT COMMENT '关联采购订单ID',
    created_by INT COMMENT '创建人ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE INDEX idx_shortage_no (shortage_no),
    INDEX idx_req_id (req_id),
    INDEX idx_status (status),
    FOREIGN KEY (req_id) REFERENCES purchase_requisition(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缺货单主表';

-- 缺货单明细表
CREATE TABLE shortage_order_item (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    shortage_order_id BIGINT NOT NULL COMMENT '缺货单ID',
    line_no INT NOT NULL COMMENT '行号',
    req_item_id BIGINT NOT NULL COMMENT '来源申领明细ID',
    reagent_id INT NOT NULL COMMENT '试剂ID',
    qty_shortage DECIMAL(10,2) NOT NULL COMMENT '缺货数量',
    est_unit_price DECIMAL(10,2) COMMENT '预估单价',
    est_amount DECIMAL(12,2) COMMENT '预估金额',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (shortage_order_id) REFERENCES shortage_order(id),
    FOREIGN KEY (req_item_id) REFERENCES purchase_requisition_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缺货单明细表';

-- 采购订单主表
CREATE TABLE purchase_order (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    po_no VARCHAR(32) NOT NULL COMMENT '采购订单号 PO-YYYYMMDD-NNNN',
    source_type ENUM('SHORTAGE','MANUAL','SMART_REPLENISH') NOT NULL DEFAULT 'SHORTAGE' COMMENT '订单来源',
    shortage_order_id BIGINT COMMENT '来源缺货单ID',
    req_id BIGINT COMMENT '来源申领单ID（冗余，便于追溯）',
    supplier_id INT NOT NULL COMMENT '供应商ID',
    acceptance_method ENUM('SCAN','MANUAL','MIXED') NOT NULL DEFAULT 'SCAN' COMMENT '验收方式',
    status ENUM('DRAFT','SENT','PARTIAL_RECEIVED','COMPLETED','CANCELLED') DEFAULT 'DRAFT' COMMENT '订单状态',
    total_amount DECIMAL(14,2) COMMENT '订单总金额',
    order_date DATE NOT NULL COMMENT '下单日期',
    expected_arrival DATE COMMENT '预计到货日期',
    received_quantity DECIMAL(10,2) DEFAULT 0 COMMENT '已收货总数量',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE INDEX idx_po_no (po_no),
    INDEX idx_shortage_order (shortage_order_id),
    INDEX idx_req_id (req_id),
    INDEX idx_supplier (supplier_id),
    INDEX idx_status (status),
    INDEX idx_acceptance_method (acceptance_method)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单主表';

-- 采购订单明细表
CREATE TABLE purchase_order_item (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    po_id BIGINT NOT NULL COMMENT '订单ID',
    line_no INT NOT NULL COMMENT '行号',
    reagent_id INT NOT NULL COMMENT '试剂ID',
    material_type ENUM('REAGENT','CALIBRATOR','QC_MATERIAL','CONSUMABLE','OTHER') NOT NULL COMMENT '物料类型',
    specification VARCHAR(100) COMMENT '规格',
    order_quantity DECIMAL(10,2) NOT NULL COMMENT '订购数量',
    unit_price DECIMAL(10,2) NOT NULL COMMENT '单价',
    amount DECIMAL(12,2) COMMENT '行金额',
    received_quantity DECIMAL(10,2) DEFAULT 0 COMMENT '已收货数量',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (po_id) REFERENCES purchase_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单明细表';
