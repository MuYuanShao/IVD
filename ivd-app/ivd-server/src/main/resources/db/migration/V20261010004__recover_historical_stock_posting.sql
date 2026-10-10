-- Target-batch creation is evidence of historical STOCK POSTING, not a fabricated signature time.
-- Do NOT modify purchase_requisition.received_at, stock quantities, codes, or settlements.
INSERT INTO requisition_transfer_receipt(transfer_record_id,target_batch_id,received_by,received_at,evidence_source,evidence_reference)
SELECT cr.id,b.id,NULL,b.created_at,'STOCK_POSTING_RECOVERY',
       CONCAT('reagent_batch:',b.id,' created_at; consumption_record:',cr.id,'; purchase_requisition:',r.id,'; settlement verified; signature time not recovered')
FROM consumption_record cr
JOIN purchase_requisition r ON r.id=cr.requisition_id AND r.status='FULFILLED'
JOIN reagent_batch b ON b.reagent_id=cr.reagent_id AND b.batch_no=cr.batch_no AND b.warehouse_id=cr.warehouse_id
JOIN (
    SELECT 2086100387449933824 transfer_id,2086100668011122688 target_id,'2026-08-08 22:42:11.788' posted_at
    UNION ALL SELECT 2086100387491876864,2086100668048871424,'2026-08-08 22:42:11.797'
    UNION ALL SELECT 2097662712283795456,2097662712615145472,'2026-09-09 20:25:38.033'
    UNION ALL SELECT 2097662716675231744,2097662717035941888,'2026-09-09 20:25:39.088'
) verified ON verified.transfer_id=cr.id AND verified.target_id=b.id AND b.created_at=verified.posted_at
WHERE cr.record_type='TRANSFER' AND cr.quantity>0 AND cr.occurred_at<=b.created_at AND b.created_at<=r.updated_at
  AND EXISTS (SELECT 1 FROM settlement_record s WHERE s.settlement_type='REQUISITION' AND s.source_id=r.id
              AND s.reagent_id=cr.reagent_id AND s.quantity=cr.quantity AND s.amount=cr.amount AND s.settled_at>=b.created_at)
  AND NOT EXISTS (SELECT 1 FROM requisition_transfer_receipt receipt WHERE receipt.transfer_record_id=cr.id);

INSERT INTO operation_audit_log(operator_name,module,action,handler,reason,success,duration_ms,occurred_at)
SELECT 'Flyway','inventory','HISTORICAL_STOCK_POSTING_RECOVERY','V20261010004',
       CONCAT('Recovered ',COUNT(*),' unique stock-posting receipts; source=target batch creation; actual signature timestamps remain NULL; stock unchanged'),1,0,CURRENT_TIMESTAMP(3)
FROM requisition_transfer_receipt WHERE evidence_source='STOCK_POSTING_RECOVERY';
