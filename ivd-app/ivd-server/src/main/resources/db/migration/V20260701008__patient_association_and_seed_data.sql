-- ============================================
-- 脚本: V20260701008__patient_association_and_seed_data.sql
-- 说明: 患者关联追踪表 + catalog_approval_log 加列 + 全模块种子数据
-- 模块: X1/M01/M03/M04/M06/M08
-- 依赖: V20260701001 ~ V20260701007
-- ============================================

-- ==================== 1. 患者关联追踪表 ====================
CREATE TABLE patient_association (
    id BIGINT PRIMARY KEY COMMENT '主键（雪花ID）',
    batch_no VARCHAR(64) NOT NULL COMMENT '批次号',
    patient_id_hash VARCHAR(64) NOT NULL COMMENT '患者ID脱敏哈希',
    test_item_code VARCHAR(32) NOT NULL COMMENT '测试项目编码',
    test_value DECIMAL(12,4) COMMENT '测试结果值',
    test_time DATETIME(3) COMMENT '测试时间',
    audit_status VARCHAR(32) DEFAULT 'AUTO_RELEASED' COMMENT '审核状态',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_batch_no (batch_no),
    INDEX idx_patient_hash (patient_id_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='患者关联追踪表';

-- ==================== 2. catalog_approval_log 加列 ====================
ALTER TABLE catalog_approval_log
    ADD COLUMN change_summary VARCHAR(500) COMMENT '变更摘要（审批列表展示用）' AFTER change_reason;

-- ==================== 3. 种子数据 ====================

-- 3.1 仓库（7条：中心库1 + 检验科库2 + 5个组库3-7）
INSERT INTO warehouse (id, name, level, parent_id, location_code, is_active) VALUES
(1, '中心药库', 1, NULL, 'C-01', 1),
(2, '检验科库', 2, 1, 'D-01', 1),
(3, '免疫组库', 3, 2, 'G-IMMUNO', 1),
(4, '生化组库', 3, 2, 'G-BIOCHEM', 1),
(5, '临检组库', 3, 2, 'G-CLINICAL', 1),
(6, '微生物组库', 3, 2, 'G-MICRO', 1),
(7, '分子组库', 3, 2, 'G-MOLEC', 1);

-- 3.2 供应商（3条）
INSERT INTO supplier (id, name, short_name, contact_person, contact_phone, contact_email, address, is_active) VALUES
(1, '国药控股股份有限公司', '国药控股', '李明', '13800001111', 'liming@sinopharm.com', '上海市黄浦区', 1),
(2, '九州通医药集团', '九州通', '王芳', '13800002222', 'wangfang@jzt.com', '湖北省武汉市', 1),
(3, '华润医药集团有限公司', '华润医药', '陈伟', '13800003333', 'chenwei@crpharm.com', '北京市朝阳区', 1);

-- 3.3 试剂目录（8条）
INSERT INTO reagent_catalog (id, his_code, generic_name, material_type, manufacturer, specification, unit, storage_condition, test_groups, udi_di, gtin, insurance_code, registration_cert_no, detection_method, tests_per_kit, price_per_unit, stability_days_after_open, min_stock_level, max_stock_level, lead_time_days, device_bind_mode, approval_status) VALUES
(1, 'IVD-IMMUNO-001', 'CK-MB检测试剂盒', 'REAGENT', '九强生物', '100T', '盒', '2-8°C', '免疫', '06927634501234', '06927634501234', 'C0000010000001', '国械注准20253400001', '免疫抑制法', 100, 1200.00, 30, 20, 200, 7, 'FORCE', 'APPROVED'),
(2, 'IVD-BIOCHEM-001', 'ALT测定试剂盒', 'REAGENT', '美康生物', '200T', '盒', '2-8°C', '生化', '06927634501235', '06927634501235', 'C0000010000002', '国械注准20253400002', '速率法', 200, 850.00, 60, 15, 150, 7, 'FLEXIBLE', 'APPROVED'),
(3, 'IVD-BIOCHEM-002', '肌酐测定试剂盒(酶法)', 'REAGENT', '九强生物', '200T', '盒', '2-8°C', '生化', '06927634501236', '06927634501236', 'C0000010000003', '国械注准20253400003', '酶法', 200, 1800.00, 45, 10, 100, 7, 'FORCE', 'APPROVED'),
(4, 'IVD-IMMUNO-002', 'CEA检测试剂盒', 'REAGENT', '罗氏诊断', '100T', '盒', '2-8°C', '免疫', '07613339001234', '07613339001234', 'C0000010000004', '国械注进20253400004', '电化学发光法', 100, 3200.00, 28, 10, 80, 14, 'FORCE', 'APPROVED'),
(5, 'IVD-CLINICAL-001', '凝血酶原时间测定试剂盒', 'REAGENT', '希森美康', '150T', '盒', '2-8°C', '临检', '04987114001234', '04987114001234', 'C0000010000005', '国械注进20253400005', '凝固法', 150, 2500.00, 14, 8, 60, 7, 'FORCE', 'APPROVED'),
(6, 'IVD-BIOCHEM-003', 'HbA1c检测试剂盒', 'REAGENT', '东曹', '100T', '盒', '2-8°C', '生化', '04567892001234', '04567892001234', 'C0000010000006', '国械注进20253400006', 'HPLC法', 100, 2800.00, 60, 5, 50, 14, 'FORCE', 'APPROVED'),
(7, 'IVD-IMMUNO-003', 'hs-CRP检测试剂盒', 'REAGENT', '九强生物', '200T', '盒', '2-8°C', '免疫', '06927634501237', '06927634501237', 'C0000010000007', '国械注准20253400007', '免疫比浊法', 200, 3200.00, 30, 10, 80, 7, 'FLEXIBLE', 'APPROVED'),
(8, 'IVD-IMMUNO-004', '降钙素原检测试剂盒', 'REAGENT', '罗氏诊断', '100T', '盒', '2-8°C', '免疫', '07613339001235', '07613339001235', 'C0000010000008', '国械注进20253400008', '电化学发光法', 100, 4500.00, 28, 5, 40, 14, 'FORCE', 'APPROVED');

-- 3.4 试剂供应商关联
INSERT INTO reagent_supplier_rel (reagent_id, supplier_id, is_default) VALUES
(1, 1, 1), (2, 2, 1), (3, 1, 1), (4, 3, 1),
(5, 1, 1), (6, 2, 1), (7, 1, 1), (8, 3, 1),
(2, 1, 0), (6, 1, 0);

-- 3.5 试剂批次（15条，含不同效期/库房）
INSERT INTO reagent_batch (id, batch_no, reagent_id, material_type, udi_di, udi_pi, manufacture_date, expiry_date, open_date, open_expiry_date, stock, unit, unit_price, avg_cost, udi_status, warehouse_id, location_code, fefo_priority) VALUES
(1001, '250612', 1, 'REAGENT', '06927634501234', '250612A001', '2025-06-12', '2027-06-12', NULL, NULL, 120, '盒', 1200.00, 1200.00, 'IN_STOCK', 1, 'A-03-12', 10),
(1002, '250608', 2, 'REAGENT', '06927634501235', '250608B001', '2025-06-08', '2027-06-08', NULL, NULL, 85, '盒', 850.00, 850.00, 'IN_STOCK', 1, 'A-02-08', 12),
(1003, '250520', 3, 'REAGENT', '06927634501236', '250520C001', '2025-05-20', '2026-12-20', '2026-06-01', '2026-07-16', 15, '盒', 1800.00, 1800.00, 'IN_USE', 2, 'D-01-05', 2),
(1004, 'LOT-CEA-2409', 4, 'REAGENT', '07613339001234', 'CEA-2409-001', '2024-09-15', '2026-09-15', NULL, NULL, 8, '盒', 3200.00, 3200.00, 'IN_STOCK', 1, 'A-05-01', 1),
(1005, 'PT-250120', 5, 'REAGENT', '04987114001234', 'PT-250120-01', '2025-01-20', '2027-01-20', NULL, NULL, 45, '盒', 2500.00, 2500.00, 'IN_STOCK', 1, 'A-04-16', 15),
(1006, 'A1C-250405', 6, 'REAGENT', '04567892001234', 'A1C-250405-01', '2025-04-05', '2027-04-05', NULL, NULL, 30, '盒', 2800.00, 2800.00, 'IN_STOCK', 1, 'A-03-20', 18),
(1007, 'CRP-250530', 7, 'REAGENT', '06927634501237', 'CRP-250530-01', '2025-05-30', '2027-05-30', NULL, NULL, 22, '盒', 3200.00, 3200.00, 'IN_STOCK', 1, 'A-05-08', 20),
(1008, 'PCT-250310', 8, 'REAGENT', '07613339001235', 'PCT-250310-01', '2025-03-10', '2027-03-10', NULL, NULL, 10, '盒', 4500.00, 4500.00, 'IN_STOCK', 1, 'A-05-12', 22),
-- 近效期批次（30天内）
(1009, 'CEA-240601', 4, 'REAGENT', '07613339001234', 'CEA-240601-01', '2024-06-01', '2026-08-01', '2026-06-15', '2026-07-13', 3, '盒', 3200.00, 3200.00, 'IN_STOCK', 1, 'A-05-02', 0),
(1010, '250415', 3, 'REAGENT', '06927634501236', '250415C002', '2025-04-15', '2026-08-15', NULL, NULL, 6, '盒', 1800.00, 1800.00, 'IN_STOCK', 2, 'D-01-03', 1),
-- 已调拨至科室库
(1011, '250612-B', 1, 'REAGENT', '06927634501234', '250612A002', '2025-06-12', '2027-06-12', NULL, NULL, 32, '盒', 1200.00, 1200.00, 'TRANSFERRED', 2, 'D-02-01', 25),
(1012, '250608-B', 2, 'REAGENT', '06927634501235', '250608B002', '2025-06-08', '2027-06-08', '2026-07-01', '2026-08-30', 25, '盒', 850.00, 850.00, 'TRANSFERRED', 2, 'D-02-02', 28),
-- 已下发至组库
(1013, '250612-C', 1, 'REAGENT', '06927634501234', '250612A003', '2025-06-12', '2027-06-12', NULL, NULL, 18, '盒', 1200.00, 1200.00, 'IN_STOCK', 3, 'G-IMMUNO-01', 30),
(1014, 'CEA-2409-B', 4, 'REAGENT', '07613339001234', 'CEA-2409-002', '2024-09-15', '2026-09-15', NULL, NULL, 4, '盒', 3200.00, 3200.00, 'IN_STOCK', 3, 'G-IMMUNO-02', 5),
(1015, '250612-D', 1, 'REAGENT', '06927634501234', '250612A004', '2025-06-12', '2027-06-12', NULL, NULL, 14, '盒', 1200.00, 1200.00, 'IN_STOCK', 4, 'G-BIOCHEM-01', 35);

-- 3.6 设备（3条）
INSERT INTO device (id, device_code, name, model, manufacturer, location_id, last_calibration_date, next_calibration_date, is_active) VALUES
(1, 'DEV-001', 'Cobas e801', 'e801', '罗氏诊断', 3, '2026-06-01', '2026-12-01', 1),
(2, 'DEV-002', 'AU5800', 'AU5800', '贝克曼库尔特', 4, '2026-05-15', '2026-11-15', 1),
(3, 'DEV-003', 'CS-5100', 'CS-5100', '希森美康', 5, '2026-06-20', '2026-12-20', 1);

-- 3.7 采购申领单（3条 + items）
INSERT INTO purchase_requisition (id, req_no, from_dept_id, to_warehouse_id, applicant_id, urgency, status, remark, created_at) VALUES
(2001, 'REQ-20260701-001', 2, 1, 1001, 'NORMAL', 'FULFILLED', '月度常规申领——7月', '2026-07-01 09:00:00'),
(2002, 'REQ-20260703-001', 2, 1, 1001, 'URGENT', 'SHORTAGE_PENDING', '肌酐试剂紧急补货', '2026-07-03 14:30:00'),
(2003, 'REQ-20260705-001', 2, 1, 1002, 'NORMAL', 'PENDING_REVIEW', '免疫组试剂补充', '2026-07-05 10:15:00');

INSERT INTO purchase_requisition_item (id, req_id, line_no, reagent_id, specification, qty_requested, stock_on_hand, qty_fulfilled, qty_shortage, unit_price, amount, created_at) VALUES
(3001, 2001, 1, 1, '100T', 30, 120, 30, 0, 1200.00, 36000.00, '2026-07-01 09:00:00'),
(3002, 2001, 2, 2, '200T', 20, 85, 20, 0, 850.00, 17000.00, '2026-07-01 09:00:00'),
(3003, 2002, 1, 3, '200T', 20, 15, 6, 14, 1800.00, 36000.00, '2026-07-03 14:30:00'),
(3004, 2002, 2, 4, '100T', 10, 8, 8, 2, 3200.00, 32000.00, '2026-07-03 14:30:00'),
(3005, 2003, 1, 7, '200T', 15, 22, 0, 0, 3200.00, 48000.00, '2026-07-05 10:15:00'),
(3006, 2003, 2, 8, '100T', 5, 10, 0, 0, 4500.00, 22500.00, '2026-07-05 10:15:00');

-- 3.8 缺货单（2条 + items）
INSERT INTO shortage_order (id, shortage_no, req_id, from_dept_id, status, urgency, suggested_supplier_id, created_by, created_at) VALUES
(4001, 'SH-20260703-001', 2002, 2, 'PENDING_PROCUREMENT', 'URGENT', 1, 1001, '2026-07-03 14:35:00'),
(4002, 'SH-20260704-001', 2002, 2, 'PURCHASING', 'URGENT', 3, 1001, '2026-07-04 09:00:00');

INSERT INTO shortage_order_item (id, shortage_order_id, line_no, req_item_id, reagent_id, qty_shortage, est_unit_price, est_amount, created_at) VALUES
(5001, 4001, 1, 3003, 3, 14, 1800.00, 25200.00, '2026-07-03 14:35:00'),
(5002, 4001, 2, 3004, 4, 2, 3200.00, 6400.00, '2026-07-03 14:35:00'),
(5003, 4002, 1, 3003, 3, 6, 1800.00, 10800.00, '2026-07-04 09:00:00');

-- 3.9 采购订单（3条 + items）
INSERT INTO purchase_order (id, po_no, source_type, shortage_order_id, req_id, supplier_id, acceptance_method, status, total_amount, order_date, expected_arrival, created_at) VALUES
(6001, 'PO-20260704-0001', 'SHORTAGE', 4002, 2002, 1, 'SCAN', 'SENT', 10800.00, '2026-07-04', '2026-07-10', '2026-07-04 09:10:00'),
(6002, 'PO-20260705-0001', 'SMART_REPLENISH', NULL, NULL, 2, 'SCAN', 'PARTIAL_RECEIVED', 42500.00, '2026-07-05', '2026-07-08', '2026-07-05 08:00:00'),
(6003, 'PO-20260706-0001', 'MANUAL', NULL, NULL, 3, 'MANUAL', 'COMPLETED', 64000.00, '2026-07-06', '2026-07-12', '2026-07-06 11:00:00');

INSERT INTO purchase_order_item (id, po_id, line_no, reagent_id, material_type, specification, order_quantity, unit_price, amount, received_quantity, created_at) VALUES
(7001, 6001, 1, 3, 'REAGENT', '200T', 6, 1800.00, 10800.00, 0, '2026-07-04 09:10:00'),
(7002, 6002, 1, 2, 'REAGENT', '200T', 50, 850.00, 42500.00, 30, '2026-07-05 08:00:00'),
(7003, 6003, 1, 4, 'REAGENT', '100T', 20, 3200.00, 64000.00, 20, '2026-07-06 11:00:00');

-- 3.10 患者关联数据（5条demo）
INSERT INTO patient_association (id, batch_no, patient_id_hash, test_item_code, test_value, test_time, audit_status) VALUES
(9001, '250612', 'P-20260618-018', 'CK-MB', 25.40, '2026-06-18 08:15:00', 'AUTO_RELEASED'),
(9002, '250520', 'P-20260618-022', 'CREA', 98.50, '2026-06-18 09:30:00', 'AUTO_RELEASED'),
(9003, '250520', 'P-20260618-025', 'CREA', 112.30, '2026-06-18 10:45:00', 'HELD_REVIEW'),
(9004, '250608', 'P-20260618-031', 'ALT', 45.20, '2026-06-18 11:00:00', 'AUTO_RELEASED'),
(9005, 'LOT-CEA-2409', 'P-20260618-035', 'CEA', 8.90, '2026-06-18 14:20:00', 'WARN_RELEASE');

-- 3.11 消费记录（4条demo）
INSERT INTO consumption_record (id, record_type, batch_no, reagent_id, material_type, device_id, quantity, unit, unit_price, amount, purpose, warehouse_id, operator_id, remark, occurred_at, created_at) VALUES
(8001, 'CONSUME', '250612', 1, 'REAGENT', 1, 2, '盒', 1200.00, 2400.00, 'ROUTINE', 3, 1001, 'CK-MB日常检测', '2026-07-07 08:30:00', '2026-07-07 08:30:00'),
(8002, 'CONSUME', '250520', 3, 'REAGENT', 2, 1, '盒', 1800.00, 1800.00, 'BATCH_TEST', 2, 1002, '肌酐批量复测', '2026-07-07 09:15:00', '2026-07-07 09:15:00'),
(8003, 'INBOUND', '250612', 1, 'REAGENT', NULL, 30, '盒', 1200.00, 36000.00, NULL, 1, 1001, '采购到货入库', '2026-07-06 16:00:00', '2026-07-06 16:00:00'),
(8004, 'CONSUME', '250608', 2, 'REAGENT', 2, 3, '盒', 850.00, 2550.00, 'EQUIP_MAINT', 4, 1002, '设备维护校准', '2026-07-07 07:00:00', '2026-07-07 07:00:00');
