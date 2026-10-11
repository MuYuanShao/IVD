-- Additive only: retain all historical approval records.
CREATE TABLE catalog_draft (
  reagent_id BIGINT PRIMARY KEY,
  institution_id BIGINT NOT NULL DEFAULT 1,
  snapshot JSON NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  INDEX idx_catalog_draft_owner(institution_id),
  FOREIGN KEY (reagent_id) REFERENCES reagent_catalog(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE catalog_change_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  institution_id BIGINT NOT NULL DEFAULT 1,
  reagent_id BIGINT NOT NULL,
  reagent_name VARCHAR(255) NOT NULL,
  his_code VARCHAR(100) NOT NULL,
  change_type ENUM('CREATE','UPDATE','DELETE','DISABLE') NOT NULL,
  field_changes JSON NOT NULL,
  operator_id BIGINT,
  operator_name VARCHAR(100) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  INDEX idx_catalog_change_owner(institution_id,created_at,id),
  INDEX idx_catalog_change_reagent(institution_id,reagent_id),
  FOREIGN KEY (reagent_id) REFERENCES reagent_catalog(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TRIGGER catalog_change_log_no_update BEFORE UPDATE ON catalog_change_log
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='catalog change history is append-only';
CREATE TRIGGER catalog_change_log_no_delete BEFORE DELETE ON catalog_change_log
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='catalog change history is append-only';
