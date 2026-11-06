CREATE TABLE purchase_order_number_sequence (
    institution_id BIGINT NOT NULL,
    order_day CHAR(8) NOT NULL,
    last_number INT NOT NULL,
    PRIMARY KEY (institution_id, order_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO purchase_order_number_sequence (institution_id, order_day, last_number)
SELECT institution_id, SUBSTRING(po_no, 4, 8),
       MAX(CAST(RIGHT(po_no, 4) AS UNSIGNED))
FROM purchase_order
WHERE po_no REGEXP '^PO-[0-9]{8}-[0-9]{4}$'
GROUP BY institution_id, SUBSTRING(po_no, 4, 8);
