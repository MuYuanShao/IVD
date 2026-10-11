-- Additive lifecycle/provenance rollout. No historical quantities, amounts or evidence are rewritten.
ALTER TABLE reagent_batch
 ADD COLUMN inventory_generation INT NOT NULL DEFAULT 0,
 ADD COLUMN origin_purchase_order_item_id BIGINT NULL,
 ADD COLUMN settlement_mode VARCHAR(32) NULL,
 DROP INDEX uk_reagent_batch_warehouse,
 ADD UNIQUE KEY uk_reagent_batch_warehouse(institution_id,reagent_id,batch_no,warehouse_id,inventory_generation),
 ADD INDEX idx_batch_purchase_origin(institution_id,origin_purchase_order_item_id);
ALTER TABLE consumption_record ADD COLUMN settlement_mode VARCHAR(32) NULL;
ALTER TABLE settlement_record MODIFY settlement_type ENUM('REQUISITION','CONSUMPTION','TRANSFER','INBOUND') NOT NULL;
CREATE TABLE settlement_event (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 institution_id BIGINT NOT NULL,
 settlement_id BIGINT NOT NULL,
 from_status VARCHAR(32) NULL,
 to_status VARCHAR(32) NOT NULL,
 reason VARCHAR(500) NOT NULL,
 operator_id BIGINT NULL,
 operator_name VARCHAR(100) NOT NULL,
 created_at DATETIME(3) NOT NULL,
 INDEX idx_settlement_event_owner(institution_id,settlement_id,id),
 FOREIGN KEY(settlement_id) REFERENCES settlement_record(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TRIGGER settlement_event_no_update BEFORE UPDATE ON settlement_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='settlement event is append-only';
CREATE TRIGGER settlement_event_no_delete BEFORE DELETE ON settlement_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='settlement event is append-only';
