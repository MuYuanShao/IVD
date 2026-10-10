-- Maintenance-window migration: physical identities, stock, prices and original log contents remain unchanged.
CREATE TABLE institution (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(64) NOT NULL,
 name VARCHAR(200) NOT NULL,
 type ENUM('INTERNAL','EXTERNAL') NOT NULL,
 owner_institution_id BIGINT NULL,
 contact_person VARCHAR(64) NULL,
 contact_phone VARCHAR(32) NULL,
 address VARCHAR(500) NULL,
 capabilities TEXT NULL,
 status TINYINT NOT NULL DEFAULT 1,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_institution_code(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO institution(id,code,name,type,status) VALUES(1,'HOME','本院','INTERNAL',1);

-- Adding the historical owner using DDL preserves APPEND-ONLY triggers; no historical UPDATE is used.
ALTER TABLE ai_consultation_log ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE audit_decision_log ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE audit_rule ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE catalog_approval_log ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE catalog_field_option ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE consumption_record ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE daily_inventory_snapshot ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE dept_reagent ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE device ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE device_batch_load ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE distributor ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE five_code_mapping ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE inventory_opening_balance ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE license_change_log ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE manufacturer ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE operation_audit_log ADD COLUMN institution_id BIGINT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE patient_association ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE poct_device ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE purchase_order ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE purchase_order_item ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE purchase_requisition ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE purchase_requisition_item ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE qc_result ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE reagent_batch ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE reagent_catalog ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE reagent_supplier_rel ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE requisition_transfer_receipt ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE research_project ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE settlement_record ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE shortage_order ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE shortage_order_item ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE smart_replenish_suggestion ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE supplier ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE supplier_license ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE sys_dept ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE sys_user ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE udi_backlog ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE udi_status_log ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE udi_unique_code ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE udi_unique_code_seq ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);
ALTER TABLE warehouse ADD COLUMN institution_id BIGINT NOT NULL DEFAULT 1 COMMENT '数据归属机构', ADD INDEX idx_institution(institution_id);

-- No implicit home-institution default remains for future business writes.
ALTER TABLE ai_consultation_log ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE audit_decision_log ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE audit_rule ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE catalog_approval_log ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE catalog_field_option ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE consumption_record ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE daily_inventory_snapshot ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE dept_reagent ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE device ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE device_batch_load ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE distributor ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE five_code_mapping ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE inventory_opening_balance ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE license_change_log ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE manufacturer ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE operation_audit_log ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE patient_association ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE poct_device ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE purchase_order ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE purchase_order_item ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE purchase_requisition ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE purchase_requisition_item ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE qc_result ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE reagent_batch ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE reagent_catalog ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE reagent_supplier_rel ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE requisition_transfer_receipt ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE research_project ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE settlement_record ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE shortage_order ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE shortage_order_item ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE smart_replenish_suggestion ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE supplier ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE supplier_license ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE sys_dept ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE sys_user ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE udi_backlog ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE udi_status_log ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE udi_unique_code ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE udi_unique_code_seq ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE warehouse ALTER COLUMN institution_id DROP DEFAULT;
ALTER TABLE audit_rule DROP INDEX idx_rule_id, ADD UNIQUE KEY idx_rule_id(institution_id,rule_id);
ALTER TABLE catalog_field_option DROP INDEX uk_catalog_field_option, ADD UNIQUE KEY uk_catalog_field_option(institution_id,field_code,option_value,is_deleted);
ALTER TABLE daily_inventory_snapshot DROP INDEX idx_snapshot, ADD UNIQUE KEY idx_snapshot(institution_id,snapshot_date,reagent_id,warehouse_id);
ALTER TABLE dept_reagent DROP INDEX uk_dept_reagent, ADD UNIQUE KEY uk_dept_reagent(institution_id,dept_id,reagent_id);
ALTER TABLE device DROP INDEX idx_device_code, ADD UNIQUE KEY idx_device_code(institution_id,device_code);
ALTER TABLE distributor DROP INDEX idx_name, ADD UNIQUE KEY idx_name(institution_id,name);
ALTER TABLE distributor DROP INDEX uk_distributor_code, ADD UNIQUE KEY uk_distributor_code(institution_id,distributor_code);
ALTER TABLE five_code_mapping DROP INDEX idx_reagent_id, ADD UNIQUE KEY idx_reagent_id(institution_id,reagent_id);
ALTER TABLE inventory_opening_balance DROP INDEX uk_opening_batch, ADD UNIQUE KEY uk_opening_batch(institution_id,batch_id);
ALTER TABLE manufacturer DROP INDEX idx_name, ADD UNIQUE KEY idx_name(institution_id,name);
ALTER TABLE manufacturer DROP INDEX uk_manufacturer_code, ADD UNIQUE KEY uk_manufacturer_code(institution_id,manufacturer_code);
ALTER TABLE poct_device DROP INDEX idx_device_code, ADD UNIQUE KEY idx_device_code(institution_id,device_code);
ALTER TABLE purchase_order DROP INDEX idx_po_no, ADD UNIQUE KEY idx_po_no(institution_id,po_no);
ALTER TABLE purchase_requisition DROP INDEX idx_req_no, ADD UNIQUE KEY idx_req_no(institution_id,req_no);
ALTER TABLE reagent_batch DROP INDEX uk_reagent_batch_warehouse, ADD UNIQUE KEY uk_reagent_batch_warehouse(institution_id,reagent_id,batch_no,warehouse_id);
ALTER TABLE reagent_catalog DROP INDEX idx_his_code, ADD UNIQUE KEY idx_his_code(institution_id,his_code);
ALTER TABLE reagent_supplier_rel DROP INDEX idx_reagent_supplier, ADD UNIQUE KEY idx_reagent_supplier(institution_id,reagent_id,supplier_id);
ALTER TABLE requisition_transfer_receipt DROP INDEX uk_transfer_receipt, ADD UNIQUE KEY uk_transfer_receipt(institution_id,transfer_record_id);
ALTER TABLE research_project DROP INDEX uk_research_project_name, ADD UNIQUE KEY uk_research_project_name(institution_id,name);
ALTER TABLE settlement_record DROP INDEX idx_settlement_no, ADD UNIQUE KEY idx_settlement_no(institution_id,settlement_no);
ALTER TABLE settlement_record DROP INDEX uk_settlement_source_item, ADD UNIQUE KEY uk_settlement_source_item(institution_id,settlement_type,source_id,source_item_kind,source_item_id);
ALTER TABLE shortage_order DROP INDEX idx_shortage_no, ADD UNIQUE KEY idx_shortage_no(institution_id,shortage_no);
ALTER TABLE supplier DROP INDEX idx_name, ADD UNIQUE KEY idx_name(institution_id,name);
ALTER TABLE supplier DROP INDEX uk_supplier_code, ADD UNIQUE KEY uk_supplier_code(institution_id,supplier_code);
ALTER TABLE sys_dept DROP INDEX uk_dept_code, ADD UNIQUE KEY uk_dept_code(institution_id,dept_code);
ALTER TABLE sys_user DROP INDEX uk_user_code, ADD UNIQUE KEY uk_user_code(institution_id,user_code);
ALTER TABLE udi_backlog DROP INDEX idx_reagent_id, ADD UNIQUE KEY idx_reagent_id(institution_id,reagent_id);
ALTER TABLE udi_unique_code DROP INDEX uk_unique_code, ADD UNIQUE KEY uk_unique_code(institution_id,unique_code);
ALTER TABLE udi_unique_code_seq DROP PRIMARY KEY, ADD PRIMARY KEY(institution_id,date_key);
-- Login names remain globally unique because the existing login API resolves institution from the account.
ALTER TABLE consumption_record MODIFY operator_id BIGINT NULL;
ALTER TABLE device_batch_load MODIFY operator_id BIGINT NULL;
ALTER TABLE settlement_record MODIFY settled_by BIGINT NULL, MODIFY from_dept_id BIGINT NULL;
ALTER TABLE udi_status_log MODIFY operator_id BIGINT NULL;
ALTER TABLE audit_decision_log MODIFY auditor_id BIGINT NULL;
ALTER TABLE patient_association ADD COLUMN department_id BIGINT NULL COMMENT '历史未知科室不推定';
ALTER TABLE audit_decision_log ADD COLUMN department_id BIGINT NULL COMMENT '历史未知科室不推定';
ALTER TABLE device ADD COLUMN dept_id BIGINT NULL COMMENT '维护归属科室';
INSERT INTO operation_audit_log(institution_id,operator_name,module,action,handler,reason,success,duration_ms,occurred_at)
VALUES(1,'Flyway','institution','BACKFILL_OWNERSHIP','V20261010005','Legacy rows assigned to HOME by DDL; original identities, quantities, amounts and immutable log contents unchanged',1,0,CURRENT_TIMESTAMP(3));
