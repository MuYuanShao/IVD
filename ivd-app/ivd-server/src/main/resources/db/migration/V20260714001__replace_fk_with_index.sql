-- ============================================
-- 脚本: V20260714001__replace_fk_with_index.sql
-- 说明: 将所有跨模块 FOREIGN KEY 替换为 INDEX
--       遵循项目架构约定："跨模块用 INDEX 替代 FK，应用层通过 BizException 保证引用完整性"
-- 影响: 20 处 FK → INDEX（含 4 处缺失的索引补建）
-- 模块: 全局
-- 依赖: V20260701001 ~ V20260701005（全部 init 迁移完成后执行）
-- ============================================

-- Step 1: 使用存储过程遍历所有 FK 并删除
DELIMITER //

CREATE PROCEDURE ivd_drop_all_fks()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE tbl VARCHAR(64);
    DECLARE fk_name VARCHAR(64);
    DECLARE cur CURSOR FOR
        SELECT TABLE_NAME, CONSTRAINT_NAME
        FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_TYPE = 'FOREIGN KEY'
          AND TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME IN (
              'five_code_mapping', 'udi_backlog',
              'supplier_license', 'reagent_supplier_rel',
              'purchase_requisition_item', 'shortage_order', 'shortage_order_item', 'purchase_order_item',
              'warehouse', 'device', 'reagent_batch', 'device_batch_load',
              'consumption_record', 'settlement_record'
          );
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    SET FOREIGN_KEY_CHECKS = 0;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO tbl, fk_name;
        IF done THEN LEAVE read_loop; END IF;
        SET @sql = CONCAT('ALTER TABLE `', tbl, '` DROP FOREIGN KEY `', fk_name, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE cur;

    SET FOREIGN_KEY_CHECKS = 1;
END //

DELIMITER ;

CALL ivd_drop_all_fks();
DROP PROCEDURE ivd_drop_all_fks;

-- Step 2: 补建原本缺失的普通 INDEX（原 FK 列上已有 INDEX 的表无需重复添加）
-- 以下 4 张表的 FK 列在原始建表语句中缺少显式 INDEX：

-- shortage_order_item: shortage_order_id / req_item_id 无 INDEX
ALTER TABLE shortage_order_item
    ADD INDEX idx_shortage_order_id (shortage_order_id),
    ADD INDEX idx_req_item_id (req_item_id);

-- purchase_order_item: po_id 无 INDEX
ALTER TABLE purchase_order_item
    ADD INDEX idx_po_id (po_id);

-- device_batch_load: reagent_id 无独立 INDEX（仅含在 idx_device_batch 复合索引中）
ALTER TABLE device_batch_load
    ADD INDEX idx_reagent_id (reagent_id);

-- consumption_record: device_id 无 INDEX
ALTER TABLE consumption_record
    ADD INDEX idx_device_id (device_id);

-- ============================================
-- 受影响的表及 INDEX 覆盖确认
-- ============================================
-- five_code_mapping.reagent_id       → UNIQUE idx_reagent_id ✓
-- udi_backlog.reagent_id             → UNIQUE idx_reagent_id ✓
-- supplier_license.supplier_id       → INDEX idx_supplier ✓
-- reagent_supplier_rel.(reagent_id, supplier_id) → UNIQUE idx_reagent_supplier ✓
-- purchase_requisition_item.req_id   → INDEX idx_req_id ✓
-- shortage_order.req_id              → INDEX idx_req_id ✓
-- shortage_order_item.*              → 补建 idx_shortage_order_id + idx_req_item_id (see above)
-- purchase_order_item.*              → 补建 idx_po_id (see above)
-- warehouse.parent_id                → INDEX idx_parent ✓
-- device.location_id                 → INDEX idx_location ✓
-- reagent_batch.(reagent_id, warehouse_id) → INDEX idx_reagent_id + idx_warehouse ✓
-- device_batch_load.(device_id)      → 含在 idx_device_batch 复合索引中 ✓
-- device_batch_load.reagent_id       → 补建 idx_reagent_id (see above)
-- consumption_record.reagent_id      → INDEX idx_reagent ✓
-- consumption_record.device_id       → 补建 idx_device_id (see above)
-- consumption_record.warehouse_id    → INDEX idx_warehouse ✓
-- settlement_record.reagent_id       → INDEX idx_reagent ✓
