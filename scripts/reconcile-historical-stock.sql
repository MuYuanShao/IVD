-- Read-only repeatable reconciliation. Never calculate an adjustment from the residual.
WITH cutoffs AS (
    SELECT reagent_id,batch_no,MIN(effective_at) cutoff FROM inventory_opening_balance GROUP BY reagent_id,batch_no
), raw AS (
    SELECT r.id,r.reagent_id,r.batch_no COLLATE utf8mb4_unicode_ci batch_no,r.warehouse_id,r.from_warehouse_id,
           r.record_type,r.requisition_id,r.occurred_at,r.quantity,r.amount
    FROM consumption_record r LEFT JOIN cutoffs c ON c.reagent_id=r.reagent_id AND c.batch_no=r.batch_no COLLATE utf8mb4_unicode_ci
    WHERE c.cutoff IS NULL OR r.occurred_at>=c.cutoff
), movements AS (
    SELECT reagent_id,batch_no,warehouse_id,quantity delta_quantity,amount delta_amount FROM inventory_opening_balance
    UNION ALL SELECT reagent_id,batch_no,warehouse_id,quantity,amount FROM raw WHERE record_type='INBOUND'
    UNION ALL SELECT reagent_id,batch_no,warehouse_id,-quantity,-amount FROM raw WHERE record_type='CONSUME'
    UNION ALL SELECT reagent_id,batch_no,from_warehouse_id,-quantity,-amount FROM raw WHERE record_type='TRANSFER'
    UNION ALL SELECT reagent_id,batch_no,warehouse_id,quantity,amount FROM raw WHERE record_type='TRANSFER' AND requisition_id IS NULL
    UNION ALL SELECT r.reagent_id,r.batch_no,r.warehouse_id,r.quantity,r.amount FROM raw r
              JOIN requisition_transfer_receipt receipt ON receipt.transfer_record_id=r.id WHERE r.record_type='TRANSFER'
), ledger AS (
    SELECT reagent_id,batch_no,warehouse_id,SUM(delta_quantity) expected_quantity,SUM(delta_amount) expected_amount
    FROM movements GROUP BY reagent_id,batch_no,warehouse_id
), actual AS (
    SELECT reagent_id,batch_no COLLATE utf8mb4_unicode_ci batch_no,warehouse_id,SUM(stock) actual_quantity,SUM(stock*COALESCE(unit_price,avg_cost,0)) actual_amount
    FROM reagent_batch GROUP BY reagent_id,batch_no,warehouse_id
), identities AS (
    SELECT reagent_id,batch_no,warehouse_id FROM actual UNION SELECT reagent_id,batch_no,warehouse_id FROM ledger
)
SELECT i.reagent_id,i.batch_no,i.warehouse_id,
       COALESCE(a.actual_quantity,0) actual_quantity,COALESCE(l.expected_quantity,0) expected_quantity,
       COALESCE(a.actual_quantity,0)-COALESCE(l.expected_quantity,0) quantity_difference,
       COALESCE(a.actual_amount,0) actual_amount,COALESCE(l.expected_amount,0) expected_amount,
       COALESCE(a.actual_amount,0)-COALESCE(l.expected_amount,0) amount_difference
FROM identities i LEFT JOIN actual a ON a.reagent_id=i.reagent_id AND a.batch_no=i.batch_no AND a.warehouse_id=i.warehouse_id
LEFT JOIN ledger l ON l.reagent_id=i.reagent_id AND l.batch_no=i.batch_no AND l.warehouse_id=i.warehouse_id
ORDER BY i.reagent_id,i.batch_no,i.warehouse_id;

-- Records excluded from accounting are still present and inspectable.
SELECT r.id,r.record_type,r.occurred_at,o.cutoff,'BEFORE_OPENING_IMPORT' exclusion_reason
FROM consumption_record r JOIN (
    SELECT reagent_id,batch_no,MIN(effective_at) cutoff FROM inventory_opening_balance GROUP BY reagent_id,batch_no
) o ON o.reagent_id=r.reagent_id AND o.batch_no=r.batch_no COLLATE utf8mb4_unicode_ci WHERE r.occurred_at<o.cutoff ORDER BY r.id;
