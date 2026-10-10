-- Additive only. Apply after institution ownership migration with writes paused and verified backup.
CREATE TABLE lab_test_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    institution_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    methodology VARCHAR(100) NOT NULL,
    result_type ENUM('NUMERIC','TEXT') NOT NULL,
    unit VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY(id),
    UNIQUE KEY uk_lab_item_code(institution_id,code),
    UNIQUE KEY uk_lab_item_owner(institution_id,id),
    CONSTRAINT fk_lab_item_institution FOREIGN KEY(institution_id) REFERENCES institution(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Existing device IDs and history remain unchanged; this index permits composite ownership FK.
ALTER TABLE device ADD UNIQUE KEY uk_device_owner(institution_id,id);

CREATE TABLE lab_interval (
    id BIGINT NOT NULL AUTO_INCREMENT,
    institution_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    device_id BIGINT NULL,
    kind ENUM('REFERENCE','MEASUREMENT') NOT NULL,
    methodology VARCHAR(100) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    result_type ENUM('NUMERIC','TEXT') NOT NULL,
    sex ENUM('ANY','MALE','FEMALE') NOT NULL,
    min_age_days INT NOT NULL,
    max_age_days INT NOT NULL,
    lower_bound DECIMAL(18,6) NULL,
    upper_bound DECIMAL(18,6) NULL,
    normal_text VARCHAR(200) NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,
    active BOOLEAN NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY(id),
    KEY idx_lab_interval_match(institution_id,item_id,kind,active,valid_from,valid_to),
    CONSTRAINT fk_lab_interval_item FOREIGN KEY(institution_id,item_id) REFERENCES lab_test_item(institution_id,id),
    CONSTRAINT fk_lab_interval_device FOREIGN KEY(institution_id,device_id) REFERENCES device(institution_id,id),
    CONSTRAINT ck_lab_interval_age CHECK(min_age_days >= 0 AND max_age_days >= min_age_days AND max_age_days <= 73000),
    CONSTRAINT ck_lab_interval_dates CHECK(valid_from <= valid_to),
    CONSTRAINT ck_lab_interval_measurement CHECK(kind <> 'MEASUREMENT' OR (device_id IS NOT NULL AND result_type='NUMERIC')),
    CONSTRAINT ck_lab_interval_bounds CHECK(
        (result_type='NUMERIC' AND lower_bound IS NOT NULL AND upper_bound IS NOT NULL AND lower_bound <= upper_bound AND normal_text IS NULL)
        OR (result_type='TEXT' AND normal_text IS NOT NULL AND lower_bound IS NULL AND upper_bound IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
