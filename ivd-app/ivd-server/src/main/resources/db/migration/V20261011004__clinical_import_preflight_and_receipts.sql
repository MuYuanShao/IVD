CREATE TABLE lab_import_batch (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,department_id BIGINT NULL,actor_id BIGINT NOT NULL,
 resource VARCHAR(32) NOT NULL,content_index CHAR(64) NOT NULL,status ENUM('READY','INVALID','EXECUTING','EXECUTED') NOT NULL,
 encrypted_document MEDIUMTEXT NOT NULL,encrypted_outcome MEDIUMTEXT NULL,row_count INT NOT NULL,succeeded INT NOT NULL DEFAULT 0,
 execution_key VARCHAR(36) NULL,lease_until DATETIME(3) NULL,expires_at DATETIME(3) NOT NULL,created_at DATETIME(3) NOT NULL,updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_import_content(institution_id,actor_id,content_index),UNIQUE KEY uk_import_owner(institution_id,id),
 CONSTRAINT fk_import_institution FOREIGN KEY(institution_id) REFERENCES institution(id),
 CONSTRAINT fk_import_department FOREIGN KEY(institution_id,department_id) REFERENCES sys_dept(institution_id,id),
 CONSTRAINT ck_import_rows CHECK(row_count BETWEEN 1 AND 5000 AND succeeded BETWEEN 0 AND row_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE lab_import_unit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,institution_id BIGINT NOT NULL,department_id BIGINT NULL,batch_id BIGINT NOT NULL,
 unit_key CHAR(64) NOT NULL,status ENUM('SUCCEEDED','FAILED') NOT NULL,row_count INT NOT NULL,encrypted_outcome MEDIUMTEXT NOT NULL,created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_import_unit(institution_id,batch_id,unit_key),
 CONSTRAINT fk_import_unit_batch FOREIGN KEY(institution_id,batch_id) REFERENCES lab_import_batch(institution_id,id),
 CONSTRAINT fk_import_unit_department FOREIGN KEY(institution_id,department_id) REFERENCES sys_dept(institution_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DELIMITER $$
CREATE TRIGGER trg_lab_import_unit_no_update BEFORE UPDATE ON lab_import_unit FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import receipts are append-only'; END$$
CREATE TRIGGER trg_lab_import_unit_no_delete BEFORE DELETE ON lab_import_unit FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import receipts are append-only'; END$$
DELIMITER ;
