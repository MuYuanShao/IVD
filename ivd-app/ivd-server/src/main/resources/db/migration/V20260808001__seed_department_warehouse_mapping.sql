-- Seed the missing department-to-level-2-warehouse mapping used by requisitions.
INSERT INTO sys_dept (id, dept_name, dept_code, sort_order, status, created_at, updated_at)
VALUES (2, _utf8mb4 0xE6A380E9AA8CE7A791, 'LAB', 1, 1, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
    dept_name = VALUES(dept_name),
    status = VALUES(status),
    updated_at = NOW(3);

UPDATE warehouse
SET dept_id = 2,
    updated_at = NOW(3)
WHERE id = 2
  AND level = 2;

UPDATE purchase_requisition
SET to_warehouse_id = 2,
    updated_at = NOW(3)
WHERE from_dept_id = 2
  AND (to_warehouse_id IS NULL OR to_warehouse_id = 1);
