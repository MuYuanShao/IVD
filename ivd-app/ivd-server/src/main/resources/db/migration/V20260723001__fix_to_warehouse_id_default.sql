-- V20260723001__fix_to_warehouse_id_default.sql
-- 修复 to_warehouse_id 默认值问题 + 增加 SHIPPED 状态

-- Step 1: 修正历史数据（to_warehouse_id=1 的记录，按 from_dept_id 匹配正确科室库）
UPDATE purchase_requisition pr
    INNER JOIN warehouse w ON w.dept_id = pr.from_dept_id AND w.level = 2 AND w.status = 1
SET pr.to_warehouse_id = w.id
WHERE pr.to_warehouse_id = 1;

-- Step 2: 移除 DEFAULT 1 约束
ALTER TABLE purchase_requisition
    MODIFY COLUMN to_warehouse_id BIGINT NOT NULL COMMENT '目标科室库ID（关联 warehouse.id）';

-- Step 3: 增加 SHIPPED（待签收）状态
ALTER TABLE purchase_requisition
    MODIFY COLUMN status enum('DRAFT','PENDING_REVIEW','REVIEWED','PICKING','PARTIAL_FULFILLED','SHIPPED','FULFILLED','SHORTAGE_PENDING','CANCELLED') DEFAULT 'DRAFT';
