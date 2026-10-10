-- Each shipment can enter department stock exactly once, even across partial receipts.
CREATE TABLE requisition_transfer_receipt (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transfer_record_id BIGINT NOT NULL,
    target_batch_id BIGINT NULL,
    received_by BIGINT NULL,
    received_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_transfer_receipt (transfer_record_id),
    KEY idx_receipt_time (received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Preserve evidence for already signed historical requests; never re-enter their stock.
INSERT INTO requisition_transfer_receipt(transfer_record_id, received_by, received_at)
SELECT cr.id, NULL, r.received_at
FROM consumption_record cr JOIN purchase_requisition r ON r.id=cr.requisition_id
WHERE cr.record_type='TRANSFER' AND r.status='FULFILLED' AND r.received_at IS NOT NULL;
