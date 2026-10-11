CREATE TABLE lab_report (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,department_id BIGINT NOT NULL,
 sample_id BIGINT NOT NULL,patient_id BIGINT NOT NULL,root_id BIGINT NULL,edition INT NOT NULL,
 number_index CHAR(64) NOT NULL,status ENUM('DRAFT','PENDING_REVIEW','REVIEWED','RETURNED','SIGNED','REVOKED','SUPERSEDED') NOT NULL,
 encrypted_snapshot MEDIUMTEXT NOT NULL,version INT NOT NULL DEFAULT 0,created_by BIGINT NOT NULL,
 reviewed_by BIGINT NULL,signed_by BIGINT NULL,signed_at DATETIME(3) NULL,pdf_key VARCHAR(256) NULL,pdf_digest CHAR(64) NULL,
 created_at DATETIME(3) NOT NULL,updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_lab_report_number(institution_id,number_index,edition),
 UNIQUE KEY uk_lab_report_owner(institution_id,department_id,id),
 CONSTRAINT fk_report_sample FOREIGN KEY(institution_id,department_id,sample_id) REFERENCES lab_sample(institution_id,department_id,id),
 CONSTRAINT fk_report_patient FOREIGN KEY(institution_id,patient_id) REFERENCES lab_patient(institution_id,id),
 CONSTRAINT fk_report_root FOREIGN KEY(institution_id,department_id,root_id) REFERENCES lab_report(institution_id,department_id,id),
 CONSTRAINT ck_report_signed CHECK(status NOT IN ('SIGNED','REVOKED','SUPERSEDED') OR (signed_by IS NOT NULL AND signed_at IS NOT NULL AND pdf_key IS NOT NULL AND pdf_digest IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE lab_report_result (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,department_id BIGINT NOT NULL,
 report_id BIGINT NOT NULL,result_id BIGINT NOT NULL,result_version INT NOT NULL,
 UNIQUE KEY uk_report_result(institution_id,report_id,result_id),
 CONSTRAINT fk_report_result_report FOREIGN KEY(institution_id,department_id,report_id) REFERENCES lab_report(institution_id,department_id,id),
 CONSTRAINT fk_report_result_result FOREIGN KEY(institution_id,department_id,result_id) REFERENCES lab_result(institution_id,department_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE lab_report_event (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,department_id BIGINT NOT NULL,
 report_id BIGINT NOT NULL,action VARCHAR(32) NOT NULL,from_status VARCHAR(32) NULL,to_status VARCHAR(32) NOT NULL,
 encrypted_snapshot MEDIUMTEXT NOT NULL,actor_id BIGINT NOT NULL,occurred_at DATETIME(3) NOT NULL,
 KEY idx_report_event(institution_id,department_id,report_id,id),
 CONSTRAINT fk_report_event_report FOREIGN KEY(institution_id,department_id,report_id) REFERENCES lab_report(institution_id,department_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DELIMITER $$
CREATE TRIGGER trg_lab_report_event_no_update BEFORE UPDATE ON lab_report_event FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Report events are append-only'; END$$
CREATE TRIGGER trg_lab_report_event_no_delete BEFORE DELETE ON lab_report_event FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Report events are append-only'; END$$
DELIMITER ;
