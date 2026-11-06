CREATE TABLE receipt_virtual_batch_sequence (
    institution_id BIGINT NOT NULL,
    receipt_day CHAR(8) NOT NULL,
    last_number INT NOT NULL,
    PRIMARY KEY(institution_id, receipt_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO receipt_virtual_batch_sequence(institution_id, receipt_day, last_number)
SELECT institution_id, SUBSTRING(batch_no,12,8), MAX(CAST(RIGHT(batch_no,3) AS UNSIGNED))
FROM reagent_batch WHERE batch_no REGEXP '^BATCH-CONS-[0-9]{8}-[0-9]{3}$'
GROUP BY institution_id, SUBSTRING(batch_no,12,8);
