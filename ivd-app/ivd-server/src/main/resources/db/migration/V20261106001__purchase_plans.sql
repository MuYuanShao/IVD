CREATE TABLE purchase_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    plan_no VARCHAR(64) NULL,
    source_type ENUM('MANUAL','SHORTAGE','SMART_REPLENISH') NOT NULL,
    shortage_order_id BIGINT NULL,
    status ENUM('DRAFT','PENDING_REVIEW','APPROVED','REJECTED','ORDERED') NOT NULL DEFAULT 'DRAFT',
    order_date DATE NOT NULL,
    expected_delivery_date DATE NULL,
    settlement_mode VARCHAR(32) NOT NULL,
    remark VARCHAR(1000) NULL,
    created_by BIGINT NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(3) NULL,
    review_comment VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_plan_institution_no(institution_id,plan_no),
    UNIQUE KEY uk_plan_shortage(institution_id,shortage_order_id),
    KEY idx_plan_institution_status(institution_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE purchase_plan_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    reagent_id BIGINT NOT NULL,
    supplier_id INT NOT NULL,
    suggestion_id BIGINT NULL,
    reagent_name VARCHAR(200) NOT NULL,
    material_type ENUM('REAGENT','CALIBRATOR','QC_MATERIAL','CONSUMABLE','OTHER') NOT NULL,
    acceptance_method ENUM('SCAN','MANUAL') NOT NULL,
    quantity DECIMAL(10,2) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_plan_item_line(institution_id,plan_id,line_no),
    UNIQUE KEY uk_plan_item_suggestion(institution_id,suggestion_id),
    CONSTRAINT ck_plan_item_quantity CHECK(quantity>0),
    CONSTRAINT ck_plan_item_price CHECK(unit_price>=0 AND amount>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE purchase_plan_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    operator_id BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    snapshot LONGTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_plan_event(institution_id,plan_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- New columns distinguish historical evidence without changing any existing business column.
CREATE TABLE smart_replenish_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    analysis_days INT NOT NULL,
    batch_no VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_replenish_run_batch(institution_id,batch_no),
    KEY idx_replenish_run_days(institution_id,analysis_days,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE purchase_order
    ADD COLUMN plan_id BIGINT NULL,
    ADD COLUMN approval_origin ENUM('LEGACY','PLAN_REQUIRED','PLAN','DIRECT_RECEIPT') NOT NULL DEFAULT 'LEGACY',
    ADD KEY idx_order_plan(institution_id,plan_id);
ALTER TABLE purchase_order ALTER COLUMN approval_origin SET DEFAULT 'PLAN_REQUIRED';

DELIMITER $$
CREATE TRIGGER purchase_plan_event_no_update BEFORE UPDATE ON purchase_plan_event FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Purchase plan events are append-only';
END$$
CREATE TRIGGER purchase_plan_event_no_delete BEFORE DELETE ON purchase_plan_event FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Purchase plan events are append-only';
END$$
DELIMITER ;
