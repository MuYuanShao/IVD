-- Supplier lots are only unique within a reagent and a warehouse.
ALTER TABLE reagent_batch DROP INDEX idx_batch_warehouse,
    ADD UNIQUE KEY uk_reagent_batch_warehouse (reagent_id,batch_no,warehouse_id);

-- Already expired batches and overdue calibration must also be held.
UPDATE audit_rule SET rule_condition='{"field":"expiryDays","operator":"LTE","value":7}'
WHERE rule_id='R-BATCH-001'
  AND JSON_UNQUOTE(JSON_EXTRACT(rule_condition,'$.field'))='expiryDays'
  AND JSON_UNQUOTE(JSON_EXTRACT(rule_condition,'$.operator'))='BETWEEN'
  AND JSON_EXTRACT(rule_condition,'$.value[0]')=0
  AND JSON_EXTRACT(rule_condition,'$.value[1]')=7;
UPDATE audit_rule SET rule_condition='{"field":"deviceCalibrationDays","operator":"LTE","value":7}'
WHERE rule_id='R-BATCH-004'
  AND JSON_UNQUOTE(JSON_EXTRACT(rule_condition,'$.field'))='deviceCalibrationDays'
  AND JSON_UNQUOTE(JSON_EXTRACT(rule_condition,'$.operator'))='BETWEEN'
  AND JSON_EXTRACT(rule_condition,'$.value[0]')=0
  AND JSON_EXTRACT(rule_condition,'$.value[1]')=7;
