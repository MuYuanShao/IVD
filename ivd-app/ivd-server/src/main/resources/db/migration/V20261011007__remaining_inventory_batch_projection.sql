-- Derived read model only. reagent_batch remains the authoritative stock ledger.
CREATE TABLE inventory_remaining_batch (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 institution_id BIGINT NOT NULL,
 batch_id BIGINT NOT NULL,
 remaining_quantity DECIMAL(10,2) NOT NULL,
 source_version INT NOT NULL,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_remaining_source (batch_id),
 INDEX idx_remaining_owner (institution_id,batch_id),
 CONSTRAINT fk_remaining_batch FOREIGN KEY(batch_id) REFERENCES reagent_batch(id) ON DELETE CASCADE,
 CONSTRAINT ck_remaining_positive CHECK (remaining_quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO inventory_remaining_batch(institution_id,batch_id,remaining_quantity,source_version,created_at,updated_at)
SELECT institution_id,id,stock,COALESCE(version,0),CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)
FROM reagent_batch WHERE stock>0;

DELIMITER $$
CREATE TRIGGER remaining_batch_insert AFTER INSERT ON reagent_batch FOR EACH ROW
BEGIN
 IF NEW.stock>0 THEN
  INSERT INTO inventory_remaining_batch(institution_id,batch_id,remaining_quantity,source_version,created_at,updated_at)
  VALUES(NEW.institution_id,NEW.id,NEW.stock,COALESCE(NEW.version,0),CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));
 END IF;
END$$
CREATE TRIGGER remaining_batch_update AFTER UPDATE ON reagent_batch FOR EACH ROW
BEGIN
 IF NEW.stock>0 THEN
  INSERT INTO inventory_remaining_batch(institution_id,batch_id,remaining_quantity,source_version,created_at,updated_at)
  VALUES(NEW.institution_id,NEW.id,NEW.stock,COALESCE(NEW.version,0),CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))
  ON DUPLICATE KEY UPDATE institution_id=NEW.institution_id,remaining_quantity=NEW.stock,
   source_version=COALESCE(NEW.version,0),updated_at=CURRENT_TIMESTAMP(3);
 ELSE
  DELETE FROM inventory_remaining_batch WHERE batch_id=NEW.id;
 END IF;
END$$
DELIMITER ;
