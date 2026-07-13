-- ============================================
-- 脚本: V20260701002__init_supplier_license.sql
-- 说明: M03 供应商 + 证照管理
-- 模块: M03 证照管理
-- 依赖: V20260701001
-- ============================================

-- 供应商主表
CREATE TABLE supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(200) NOT NULL COMMENT '供应商名称',
    short_name VARCHAR(64) COMMENT '简称',
    contact_person VARCHAR(64) COMMENT '联系人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    contact_email VARCHAR(128) COMMENT '联系邮箱',
    address VARCHAR(256) COMMENT '地址',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否活跃',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商主表';

-- 证照主表
CREATE TABLE supplier_license (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    license_type ENUM('PRODUCT_REG','PRODUCTION','OPERATION','GMP','GSP','OTHER') NOT NULL COMMENT '证照类型',
    cert_no VARCHAR(128) NOT NULL COMMENT '证号',
    issuing_authority VARCHAR(256) COMMENT '发证机关',
    issue_date DATE NOT NULL COMMENT '发证日期',
    expire_date DATE NOT NULL COMMENT '有效期至',
    status ENUM('ACTIVE','EXPIRING','EXPIRED','FROZEN') NOT NULL DEFAULT 'ACTIVE' COMMENT '证照状态',
    attachments JSON COMMENT '附件列表 [{"name":"...","object_key":"...","size":...,"type":"pdf/img","uploaded_at":"...","uploaded_by":"..."}]',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_supplier (supplier_id),
    INDEX idx_status_expire (status, expire_date),
    FOREIGN KEY (supplier_id) REFERENCES supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='证照主表';

-- 证照变更记录表
CREATE TABLE license_change_log (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    license_id BIGINT NOT NULL COMMENT '证照ID',
    change_type ENUM('CREATE','RENEW','UPDATE','FREEZE','UNFREEZE') NOT NULL COMMENT '变更类型',
    old_expire_date DATE COMMENT '变更前有效期',
    new_expire_date DATE COMMENT '变更后有效期',
    operator_id BIGINT NOT NULL COMMENT '操作人ID',
    operator_name VARCHAR(64) COMMENT '操作人姓名',
    note TEXT COMMENT '备注',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_license (license_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='证照变更记录表';

-- 试剂-供应商关联表
CREATE TABLE reagent_supplier_rel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    reagent_id BIGINT NOT NULL COMMENT '试剂ID',
    supplier_id BIGINT NOT NULL COMMENT '供应商ID',
    is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认供应商',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE INDEX idx_reagent_supplier (reagent_id, supplier_id),
    FOREIGN KEY (reagent_id) REFERENCES reagent_catalog(id),
    FOREIGN KEY (supplier_id) REFERENCES supplier(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试剂供应商关联表';
