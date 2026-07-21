-- ============================================
-- 脚本: V20260715001__fix_dept_user_id_types.sql
-- 说明: 修复采购模块中科室ID/用户ID列类型，与 sys_dept/sys_user 统一为 BIGINT
-- 模块: M04 采购管理
-- 依赖: V20260713003 (sys_dept/sys_user), V20260701003 (procurement)
-- ============================================

ALTER TABLE purchase_requisition
    MODIFY COLUMN from_dept_id BIGINT NOT NULL COMMENT '申领科室ID（关联 sys_dept.id）',
    MODIFY COLUMN applicant_id BIGINT NOT NULL COMMENT '申请人ID（关联 sys_user.id）',
    MODIFY COLUMN to_warehouse_id BIGINT NOT NULL DEFAULT 1 COMMENT '目标仓库ID',
    MODIFY COLUMN reviewer_id BIGINT COMMENT '审核人ID（关联 sys_user.id）';

ALTER TABLE shortage_order
    MODIFY COLUMN from_dept_id BIGINT NOT NULL COMMENT '申领科室ID（关联 sys_dept.id）',
    MODIFY COLUMN created_by BIGINT COMMENT '创建人ID（关联 sys_user.id）';
