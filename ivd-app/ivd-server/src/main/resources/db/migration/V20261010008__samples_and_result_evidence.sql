-- Additive clinical slice. Apply only after a verified backup and paused business writes.
-- No patient seeds, legacy hashes, inventory movements or financial records are rewritten.
CREATE TABLE lab_sample (
    id BIGINT NOT NULL AUTO_INCREMENT,
    institution_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    number_index CHAR(64) NOT NULL,
    encrypted_details MEDIUMTEXT NOT NULL,
    status ENUM('COLLECTED','RECEIVED','REJECTED') NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    received_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY(id),
    UNIQUE KEY uk_lab_sample_number(institution_id,number_index),
    UNIQUE KEY uk_lab_sample_owner(institution_id,department_id,id),
    KEY idx_lab_sample_patient(institution_id,department_id,patient_id,id),
    CONSTRAINT fk_lab_sample_patient FOREIGN KEY(institution_id,patient_id) REFERENCES lab_patient(institution_id,id),
    CONSTRAINT fk_lab_sample_department FOREIGN KEY(institution_id,department_id) REFERENCES sys_dept(institution_id,id),
    CONSTRAINT ck_lab_sample_received CHECK((status='RECEIVED' AND received_at IS NOT NULL) OR (status<>'RECEIVED' AND received_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lab_sample_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    institution_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    sample_id BIGINT NOT NULL,
    from_status ENUM('COLLECTED','RECEIVED','REJECTED') NULL,
    to_status ENUM('COLLECTED','RECEIVED','REJECTED') NOT NULL,
    encrypted_reason MEDIUMTEXT NULL,
    actor_id BIGINT NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY(id),
    KEY idx_lab_sample_event(institution_id,department_id,sample_id,id),
    CONSTRAINT fk_lab_sample_event_sample FOREIGN KEY(institution_id,department_id,sample_id) REFERENCES lab_sample(institution_id,department_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lab_result (
    id BIGINT NOT NULL AUTO_INCREMENT,
    institution_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    sample_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    device_id BIGINT NULL,
    testing_institution_id BIGINT NOT NULL,
    execution_mode ENUM('INSTRUMENT','MANUAL','EXTERNAL') NOT NULL,
    encrypted_snapshot MEDIUMTEXT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY(id),
    UNIQUE KEY uk_lab_result_item(institution_id,sample_id,item_id),
    UNIQUE KEY uk_lab_result_owner(institution_id,department_id,id),
    KEY idx_lab_result_device(institution_id,department_id,device_id,id),
    CONSTRAINT fk_lab_result_sample FOREIGN KEY(institution_id,department_id,sample_id) REFERENCES lab_sample(institution_id,department_id,id),
    CONSTRAINT fk_lab_result_item FOREIGN KEY(institution_id,item_id) REFERENCES lab_test_item(institution_id,id),
    CONSTRAINT fk_lab_result_device FOREIGN KEY(institution_id,device_id) REFERENCES device(institution_id,id),
    CONSTRAINT fk_lab_result_testing_institution FOREIGN KEY(testing_institution_id) REFERENCES institution(id),
    CONSTRAINT ck_lab_result_device CHECK((execution_mode='INSTRUMENT' AND device_id IS NOT NULL) OR (execution_mode<>'INSTRUMENT' AND device_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lab_result_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    institution_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    result_id BIGINT NOT NULL,
    version INT NOT NULL,
    encrypted_snapshot MEDIUMTEXT NOT NULL,
    actor_id BIGINT NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY(id),
    UNIQUE KEY uk_lab_result_revision(institution_id,result_id,version),
    CONSTRAINT fk_lab_result_revision_result FOREIGN KEY(institution_id,department_id,result_id) REFERENCES lab_result(institution_id,department_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- These protect new evidence only. Existing immutable log triggers are not changed.
DELIMITER $$
CREATE TRIGGER trg_lab_sample_event_no_update BEFORE UPDATE ON lab_sample_event FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Sample events are append-only'; END$$
CREATE TRIGGER trg_lab_sample_event_no_delete BEFORE DELETE ON lab_sample_event FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Sample events are append-only'; END$$
CREATE TRIGGER trg_lab_result_revision_no_update BEFORE UPDATE ON lab_result_revision FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Result revisions are append-only'; END$$
CREATE TRIGGER trg_lab_result_revision_no_delete BEFORE DELETE ON lab_result_revision FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Result revisions are append-only'; END$$
DELIMITER ;
