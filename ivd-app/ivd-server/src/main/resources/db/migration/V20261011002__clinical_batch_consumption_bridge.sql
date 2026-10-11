ALTER TABLE reagent_batch ADD UNIQUE KEY uk_batch_owner(institution_id,id);
ALTER TABLE device_batch_load ADD UNIQUE KEY uk_load_owner(institution_id,id);
ALTER TABLE consumption_record ADD UNIQUE KEY uk_consumption_owner(institution_id,id);
CREATE TABLE lab_result_evidence (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 institution_id BIGINT NOT NULL,department_id BIGINT NOT NULL,
 result_id BIGINT NOT NULL,result_version INT NOT NULL,
 batch_id BIGINT NOT NULL,device_load_id BIGINT NULL,consumption_id BIGINT NULL,
 evidence_key CHAR(64) NOT NULL,encrypted_snapshot MEDIUMTEXT NOT NULL,
 actor_id BIGINT NOT NULL,created_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_result_evidence_key(institution_id,evidence_key),
 KEY idx_result_evidence(institution_id,department_id,result_id,result_version),
 CONSTRAINT fk_evidence_result FOREIGN KEY(institution_id,department_id,result_id) REFERENCES lab_result(institution_id,department_id,id),
 CONSTRAINT fk_evidence_batch FOREIGN KEY(institution_id,batch_id) REFERENCES reagent_batch(institution_id,id),
 CONSTRAINT fk_evidence_load FOREIGN KEY(institution_id,device_load_id) REFERENCES device_batch_load(institution_id,id),
 CONSTRAINT fk_evidence_consumption FOREIGN KEY(institution_id,consumption_id) REFERENCES consumption_record(institution_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DELIMITER $$
CREATE TRIGGER trg_lab_result_evidence_no_update BEFORE UPDATE ON lab_result_evidence FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Clinical evidence is append-only'; END$$
CREATE TRIGGER trg_lab_result_evidence_no_delete BEFORE DELETE ON lab_result_evidence FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Clinical evidence is append-only'; END$$
DELIMITER ;
