package com.zlth.ivd.inventory.service.impl;

import com.zlth.ivd.inventory.service.AuditInventoryContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Date;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditInventoryContextServiceImpl implements AuditInventoryContextService {
    private final JdbcTemplate jdbc;

    public Map<String, Object> resolve(String device, LocalDateTime at, String batchNo) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (at == null) return context;
        // Explicit batch numbers must still match the device's historical load interval.
        List<Map<String, Object>> batches = jdbc.queryForList(
                "SELECT DISTINCT b.*,c.device_bind_mode,c.registration_cert_no,c.stability_days_after_open,w.status warehouse_status " +
                "FROM reagent_batch b JOIN reagent_catalog c ON c.id=b.reagent_id LEFT JOIN warehouse w ON w.id=b.warehouse_id " +
                "WHERE (? IS NULL OR b.batch_no=?) AND ((? IS NOT NULL AND EXISTS (SELECT 1 FROM device_batch_load l " +
                "JOIN device d ON d.id=l.device_id WHERE l.batch_id=b.id AND (d.device_code=? OR CAST(d.id AS CHAR)=?) " +
                "AND l.load_time<=? AND (l.unload_time IS NULL OR l.unload_time>?))) " +
                "OR (? IS NULL AND ? IS NOT NULL AND c.device_bind_mode='FLEXIBLE'))",
                batchNo, batchNo, device, device, device, at, at, device, batchNo);
        if (batches.size() != 1) return context; // Ambiguous SKU/load must never be guessed.
        var batch = batches.get(0);
        context.put("batchNo", batch.get("batch_no"));
        context.put("batchStatus", batch.get("udi_status"));
        context.put("deviceBindMode", batch.get("device_bind_mode"));
        context.put("inventoryFrozen", !(batch.get("warehouse_status") instanceof Number active && active.intValue() == 1)
                || List.of("RECALLED", "SCRAPPED", "DESTROYED", "PENDING_UDI").contains(batch.get("udi_status")));
        addDays(context, "expiryDays", batch.get("expiry_date"), at);
        if (batch.get("open_date") == null) context.put("openExpiryDays", Integer.MAX_VALUE);
        else if (batch.get("open_expiry_date") != null) addDays(context, "openExpiryDays", batch.get("open_expiry_date"), at);
        else if (batch.get("stability_days_after_open") instanceof Number days) {
            var end = ((Date) batch.get("open_date")).toLocalDate().plusDays(days.longValue());
            context.put("openExpiryDays", ChronoUnit.DAYS.between(at.toLocalDate(), end));
        }
        if (device != null) {
            var devices = jdbc.queryForList("SELECT next_calibration_date FROM device WHERE is_active=1 AND (device_code=? OR CAST(id AS CHAR)=?)", device, device);
            if (devices.size() == 1) addDays(context, "deviceCalibrationDays", devices.get(0).get("next_calibration_date"), at);
        }
        var licenses = jdbc.queryForList("SELECT status,expire_date FROM supplier_license WHERE license_type='PRODUCT_REG' AND cert_no=?", batch.get("registration_cert_no"));
        if (!licenses.isEmpty()) {
            boolean unsafe = licenses.stream().anyMatch(license -> !List.of("ACTIVE", "EXPIRING").contains(license.get("status"))
                    || license.get("expire_date") == null || ((Date) license.get("expire_date")).toLocalDate().isBefore(at.toLocalDate()));
            context.put("licenseStatus", unsafe ? "EXPIRED" : "ACTIVE");
        }
        return context;
    }

    private void addDays(Map<String, Object> context, String field, Object date, LocalDateTime at) {
        if (date instanceof Date value) context.put(field, ChronoUnit.DAYS.between(at.toLocalDate(), value.toLocalDate()));
    }
}
