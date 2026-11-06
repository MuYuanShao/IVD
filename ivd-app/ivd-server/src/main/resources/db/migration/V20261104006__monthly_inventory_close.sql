CREATE TABLE monthly_inventory_close (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    period_month CHAR(7) NOT NULL,
    opening DECIMAL(24,2) NOT NULL,
    inbound DECIMAL(24,2) NOT NULL,
    outbound DECIMAL(24,2) NOT NULL,
    adjustment DECIMAL(24,2) NOT NULL,
    closing DECIMAL(24,2) NOT NULL,
    snapshot_count BIGINT NOT NULL,
    unbalanced_count BIGINT NOT NULL,
    closed_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_monthly_close_institution_period (institution_id, period_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
