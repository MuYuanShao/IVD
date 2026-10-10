-- Opening import evidence is separate from purchases: never add stock or invent an INBOUND transaction.
CREATE TABLE inventory_opening_balance (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    reagent_id BIGINT NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    quantity DECIMAL(10,2) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    effective_at DATETIME(3) NOT NULL,
    evidence_source VARCHAR(32) NOT NULL,
    evidence_reference VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_opening_batch(batch_id),
    KEY idx_opening_identity(reagent_id,batch_no,effective_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE requisition_transfer_receipt
    ADD COLUMN evidence_source VARCHAR(32) NOT NULL DEFAULT 'BUSINESS_SIGNED',
    ADD COLUMN evidence_reference VARCHAR(500) NULL;

-- Only import the literal quantities from the immutable, checksum-matched seed migration.
-- Current stock is NOT used to derive a balancing adjustment.
INSERT INTO inventory_opening_balance(batch_id,reagent_id,batch_no,warehouse_id,quantity,unit_price,amount,effective_at,evidence_source,evidence_reference)
SELECT b.id,b.reagent_id,b.batch_no,b.warehouse_id,seed.quantity,seed.price,seed.quantity*seed.price,b.created_at,'SEED_IMPORT',
       'V20260701008__patient_association_and_seed_data.sql; Flyway checksum=-1732783294; SHA256=2A39E7BF33791924D1B46BA1FCD14F1D1106924A3B254AA23D3697917DD6CC25'
FROM reagent_batch b JOIN (
    SELECT 1001 batch_id,1 reagent_id,'250612' batch_no,1 warehouse_id,120 quantity,1200 price
    UNION ALL SELECT 1002,2,'250608',1,85,850
    UNION ALL SELECT 1003,3,'250520',2,15,1800
    UNION ALL SELECT 1004,4,'LOT-CEA-2409',1,8,3200
    UNION ALL SELECT 1005,5,'PT-250120',1,45,2500
    UNION ALL SELECT 1006,6,'A1C-250405',1,30,2800
    UNION ALL SELECT 1007,7,'CRP-250530',1,22,3200
    UNION ALL SELECT 1008,8,'PCT-250310',1,10,4500
    UNION ALL SELECT 1009,4,'CEA-240601',1,3,3200
    UNION ALL SELECT 1010,3,'250415',2,6,1800
    UNION ALL SELECT 1011,1,'250612-B',2,32,1200
    UNION ALL SELECT 1012,2,'250608-B',2,25,850
    UNION ALL SELECT 1013,1,'250612-C',3,18,1200
    UNION ALL SELECT 1014,4,'CEA-2409-B',3,4,3200
    UNION ALL SELECT 1015,1,'250612-D',4,14,1200
) seed ON seed.batch_id=b.id AND seed.reagent_id=b.reagent_id
       AND seed.batch_no COLLATE utf8mb4_unicode_ci=b.batch_no COLLATE utf8mb4_unicode_ci AND seed.warehouse_id=b.warehouse_id
JOIN flyway_schema_history h ON h.version='20260701008' AND h.checksum=-1732783294 AND h.success=1
WHERE b.created_at>=h.installed_on AND b.created_at<DATE_ADD(h.installed_on,INTERVAL 1 MINUTE);

DELIMITER $$
CREATE TRIGGER trg_inventory_opening_no_update BEFORE UPDATE ON inventory_opening_balance FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='inventory opening evidence is append-only'; END$$
CREATE TRIGGER trg_inventory_opening_no_delete BEFORE DELETE ON inventory_opening_balance FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='inventory opening evidence is append-only'; END$$
DELIMITER ;

INSERT INTO operation_audit_log(operator_name,module,action,handler,reason,success,duration_ms,occurred_at)
SELECT 'Flyway','finance','HISTORICAL_OPENING_RECOVERY','V20261010003',
       CONCAT('Recovered ',COUNT(*),' seed opening rows; no stock mutation; pre-import ledger remains preserved'),1,0,CURRENT_TIMESTAMP(3)
FROM inventory_opening_balance;
