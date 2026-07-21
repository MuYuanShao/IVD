-- H2 Test Schema: Catalog-related tables (simplified for module-level testing)
-- 基于 Flyway 迁移: V20260701001 + V2026070901 + V20260710001

CREATE TABLE IF NOT EXISTS reagent_catalog (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    his_code VARCHAR(50) NOT NULL,
    generic_name VARCHAR(200) NOT NULL,
    material_type VARCHAR(20) NOT NULL DEFAULT 'REAGENT',
    manufacturer VARCHAR(200),
    distributor VARCHAR(200),
    specification VARCHAR(100),
    unit VARCHAR(20),
    storage_condition VARCHAR(100),
    test_groups VARCHAR(200),
    udi_di VARCHAR(128),
    gtin VARCHAR(14),
    insurance_code VARCHAR(27),
    registration_cert_no VARCHAR(128),
    detection_method VARCHAR(100),
    tests_per_kit INT,
    price_per_unit DECIMAL(12,2),
    stability_days_after_open INT,
    lead_time_days INT NOT NULL DEFAULT 7,
    device_bind_mode VARCHAR(20) NOT NULL DEFAULT 'FORCE',
    approval_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS five_code_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reagent_id BIGINT NOT NULL,
    udi_di VARCHAR(128) NOT NULL,
    gtin VARCHAR(14),
    insurance_code VARCHAR(27),
    nmpa_reg_no VARCHAR(128),
    his_code VARCHAR(50) NOT NULL,
    mapping_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_MAPPING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_five_code_reagent FOREIGN KEY (reagent_id) REFERENCES reagent_catalog(id)
);

CREATE TABLE IF NOT EXISTS catalog_approval_log (
    id BIGINT PRIMARY KEY,
    reagent_id BIGINT NOT NULL,
    change_type VARCHAR(20) NOT NULL,
    field_changes TEXT,
    change_reason TEXT,
    submitter_id BIGINT NOT NULL,
    submitter_name VARCHAR(64) NOT NULL,
    reviewer_id BIGINT,
    reviewer_name VARCHAR(64),
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_comment TEXT,
    submitted_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS udi_backlog (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reagent_id BIGINT NOT NULL,
    material_type VARCHAR(20) NOT NULL,
    impact_level VARCHAR(10) NOT NULL DEFAULT 'WARN',
    has_open_po TINYINT NOT NULL DEFAULT 0,
    days_pending INT DEFAULT 0,
    notified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_udi_backlog_reagent FOREIGN KEY (reagent_id) REFERENCES reagent_catalog(id)
);
