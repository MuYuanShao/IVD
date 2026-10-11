package com.zlth.ivd.inventory.service.impl;

import com.zlth.ivd.common.utils.IdGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.catalog.mapper.ReagentCatalogMapper;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.enums.UdiStatusEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.inventory.entity.*;
import com.zlth.ivd.inventory.vo.ReagentBatchVO;
import com.zlth.ivd.inventory.mapper.*;
import com.zlth.ivd.inventory.service.InventoryService;
import com.zlth.ivd.udi.mapper.UdiUniqueCodeMapper;
import com.zlth.ivd.udi.entity.UdiUniqueCode;
import com.zlth.ivd.udi.service.UniqueCodeService;
import com.zlth.ivd.udi.service.UdiStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class InventoryServiceImpl implements InventoryService {

    private final ReagentBatchMapper batchMapper;
    private final WarehouseMapper warehouseMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceBatchLoadMapper loadMapper;
    private final PatientAssociationMapper patientAssociationMapper;
    private final ReagentCatalogMapper catalogMapper;
    private final UdiUniqueCodeMapper uniqueCodeMapper;
    private final UniqueCodeService uniqueCodeService;
    private final UdiStateMachine udiStateMachine;
    private final com.zlth.ivd.inventory.service.ProcurementInventoryService stockOperations;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public PageVO<ReagentBatchVO> listBatches(int page, int pageSize, String keyword, String status, Long warehouseId,
                                               String hisCode, String reagentName, String distributor, String manufacturer, String registrationCertNo,
                                               Integer level) {
        // 如果指定了 warehouse level，先查出该层级的所有仓库 ID
        Set<Long> levelWarehouseIds = null;
        if (level != null) {
            List<Warehouse> levelWarehouses = warehouseMapper.selectList(
                    new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getLevel, level));
            levelWarehouseIds = levelWarehouses.stream().map(Warehouse::getId).collect(Collectors.toSet());
            if (levelWarehouseIds.isEmpty()) {
                return PageVO.empty(page, pageSize);
            }
        }

        // 1. 如果提供了试剂目录级筛选条件，先查出匹配的 reagentId 集合
        Set<Long> catalogFilteredIds = null;
        boolean hasCatalogFilter = hisCode != null || reagentName != null || distributor != null
                || manufacturer != null || registrationCertNo != null;
        if (hasCatalogFilter) {
            LambdaQueryWrapper<ReagentCatalog> cqw = new LambdaQueryWrapper<>();
            if (hisCode != null) cqw.like(ReagentCatalog::getHisCode, hisCode);
            if (reagentName != null) cqw.like(ReagentCatalog::getGenericName, reagentName);
            if (distributor != null) cqw.like(ReagentCatalog::getDistributor, distributor);
            if (manufacturer != null) cqw.like(ReagentCatalog::getManufacturer, manufacturer);
            if (registrationCertNo != null) cqw.like(ReagentCatalog::getRegistrationCertNo, registrationCertNo);
            List<ReagentCatalog> matchedCatalogs = catalogMapper.selectList(cqw);
            catalogFilteredIds = matchedCatalogs.stream().map(ReagentCatalog::getId).collect(Collectors.toSet());
            if (catalogFilteredIds.isEmpty()) {
                return PageVO.empty(page, pageSize);
            }
        }

        // 2. 构建批次查询
        LambdaQueryWrapper<ReagentBatch> qw = new LambdaQueryWrapper<>();
        if (status != null) qw.eq(ReagentBatch::getUdiStatus, UdiStatusEnum.valueOf(status));
        if (warehouseId != null) qw.eq(ReagentBatch::getWarehouseId, warehouseId);
        if (levelWarehouseIds != null) qw.in(ReagentBatch::getWarehouseId, levelWarehouseIds);
        if (catalogFilteredIds != null) qw.in(ReagentBatch::getReagentId, catalogFilteredIds);
        if (keyword != null) qw.and(w -> w.like(ReagentBatch::getBatchNo, keyword).or().like(ReagentBatch::getUdiDi, keyword));
        qw.orderByAsc(ReagentBatch::getExpiryDate);
        Page<ReagentBatch> p = batchMapper.selectPage(new Page<>(page, pageSize), qw);

        // 3. 批量加载试剂目录信息
        Set<Long> reagentIds = p.getRecords().stream().map(ReagentBatch::getReagentId).collect(Collectors.toSet());
        Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
        if (!reagentIds.isEmpty()) {
            List<ReagentCatalog> catalogs = catalogMapper.selectBatchIds(reagentIds);
            for (ReagentCatalog cat : catalogs) catalogMap.put(cat.getId(), cat);
        }

        // 4. 组装 VO
        List<ReagentBatchVO> records = new ArrayList<>();
        for (ReagentBatch batch : p.getRecords()) {
            ReagentBatchVO vo = new ReagentBatchVO();
            ReagentCatalog cat = catalogMap.get(batch.getReagentId());
            vo.setId(batch.getId()); vo.setBatchNo(batch.getBatchNo());
            vo.setReagentId(batch.getReagentId());
            vo.setReagentName(cat != null ? cat.getGenericName() : "试剂#" + batch.getReagentId());
            vo.setGenericName(cat != null ? cat.getGenericName() : null);
            vo.setHisCode(cat != null ? cat.getHisCode() : null);
            vo.setSpecification(cat != null ? cat.getSpecification() : null);
            vo.setDistributor(cat != null ? cat.getDistributor() : null);
            vo.setManufacturer(cat != null ? cat.getManufacturer() : null);
            vo.setRegistrationCertNo(cat != null ? cat.getRegistrationCertNo() : null);
            vo.setInsuranceCode(cat != null ? cat.getInsuranceCode() : null);
            vo.setIsVolumeBased(cat != null ? cat.getIsVolumeBased() : 0);
            vo.setVolumeType(cat != null ? cat.getVolumeType() : null);
            vo.setIsChargeable(cat != null ? cat.getIsChargeable() : 0);
            vo.setMaterialType(batch.getMaterialType());
            vo.setUdiDi(batch.getUdiDi()); vo.setUdiPi(batch.getUdiPi());
            vo.setManufactureDate(batch.getManufactureDate());
            vo.setExpiryDate(batch.getExpiryDate());
            vo.setStock(batch.getStock()); vo.setUnit(batch.getUnit());
            vo.setUnitPrice(batch.getUnitPrice());
            vo.setAmount(batch.getStock() != null && batch.getUnitPrice() != null
                    ? batch.getStock().multiply(batch.getUnitPrice()) : BigDecimal.ZERO);
            vo.setUdiStatus(batch.getUdiStatus());
            vo.setWarehouseId(batch.getWarehouseId());
            vo.setLocationCode(batch.getLocationCode());
            vo.setLocation(batch.getLocationCode() != null ? batch.getLocationCode() : "-");
            vo.setInboundDate(batch.getCreatedAt() != null ? batch.getCreatedAt().toLocalDate().toString() : null);
            vo.setVersion(batch.getVersion());
            vo.setCreatedAt(batch.getCreatedAt()); vo.setUpdatedAt(batch.getUpdatedAt());
            records.add(vo);
        }
        return new PageVO<>(p.getTotal(), page, pageSize, records);
    }

    @Override
    public PageVO<Warehouse> listWarehouses(int page, int pageSize) {
        Page<Warehouse> p = warehouseMapper.selectPage(new Page<>(page, pageSize), null);
        return new PageVO<>(p.getTotal(), page, pageSize, p.getRecords());
    }

    @Override
    public List<Map<String, Object>> getFefoRecommendations(Long reagentId, Long warehouseId) {
        // FEFO: 按效期升序排列（最早到期优先出库）；reagentId 为空时返回该仓库下所有试剂的FEFO推荐
        LambdaQueryWrapper<ReagentBatch> qw = new LambdaQueryWrapper<>();
        qw.eq(reagentId != null, ReagentBatch::getReagentId, reagentId)
          .eq(warehouseId != null, ReagentBatch::getWarehouseId, warehouseId)
          .eq(ReagentBatch::getUdiStatus, UdiStatusEnum.IN_STOCK)
          .gt(ReagentBatch::getStock, BigDecimal.ZERO)
          .orderByAsc(ReagentBatch::getExpiryDate);
        List<ReagentBatch> batches = batchMapper.selectList(qw);

        // 批量加载试剂目录
        Set<Long> reagentIds = batches.stream().map(ReagentBatch::getReagentId).collect(Collectors.toSet());
        Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
        if (!reagentIds.isEmpty()) {
            List<ReagentCatalog> catalogs = catalogMapper.selectBatchIds(reagentIds);
            for (ReagentCatalog cat : catalogs) catalogMap.put(cat.getId(), cat);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ReagentBatch batch : batches) {
            Map<String, Object> row = new LinkedHashMap<>();
            ReagentCatalog cat = catalogMap.get(batch.getReagentId());
            row.put("id", batch.getId());
            row.put("batchNo", batch.getBatchNo());
            row.put("reagentId", batch.getReagentId());
            row.put("reagentName", cat != null ? cat.getGenericName() : null);
            row.put("hisCode", cat != null ? cat.getHisCode() : null);
            row.put("genericName", cat != null ? cat.getGenericName() : null);
            row.put("specification", cat != null ? cat.getSpecification() : null);
            row.put("manufacturer", cat != null ? cat.getManufacturer() : null);
            row.put("distributor", cat != null ? cat.getDistributor() : null);
            row.put("registrationCertNo", cat != null ? cat.getRegistrationCertNo() : null);
            row.put("insuranceCode", cat != null ? cat.getInsuranceCode() : null);
            row.put("isVolumeBased", cat != null ? cat.getIsVolumeBased() : 0);
            row.put("volumeType", cat != null ? cat.getVolumeType() : null);
            row.put("isChargeable", cat != null ? cat.getIsChargeable() : 0);
            row.put("manufactureDate", batch.getManufactureDate() != null ? batch.getManufactureDate().toString() : null);
            row.put("expiryDate", batch.getExpiryDate() != null ? batch.getExpiryDate().toString() : null);
            row.put("stock", batch.getStock());
            row.put("unitPrice", batch.getUnitPrice());
            row.put("amount", batch.getStock() != null && batch.getUnitPrice() != null
                    ? batch.getStock().multiply(batch.getUnitPrice()) : BigDecimal.ZERO);
            row.put("inboundDate", batch.getCreatedAt() != null ? batch.getCreatedAt().toLocalDate().toString() : null);
            row.put("location", batch.getLocationCode() != null ? batch.getLocationCode() : "-");
            row.put("udiDi", batch.getUdiDi());
            result.add(row);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getExpiryAlerts(int days, Long warehouseId) {
        LocalDate threshold = LocalDate.now().plusDays(days);
        LambdaQueryWrapper<ReagentBatch> qw = new LambdaQueryWrapper<>();
        qw.le(ReagentBatch::getExpiryDate, threshold)
          .eq(warehouseId != null, ReagentBatch::getWarehouseId, warehouseId)
          .gt(ReagentBatch::getStock, BigDecimal.ZERO)
          .orderByAsc(ReagentBatch::getExpiryDate);
        List<ReagentBatch> batches = batchMapper.selectList(qw);

        // 批量加载试剂目录
        Set<Long> reagentIds = batches.stream().map(ReagentBatch::getReagentId).collect(Collectors.toSet());
        Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
        if (!reagentIds.isEmpty()) {
            List<ReagentCatalog> catalogs = catalogMapper.selectBatchIds(reagentIds);
            for (ReagentCatalog cat : catalogs) catalogMap.put(cat.getId(), cat);
        }

        // 批量加载仓库名称
        Set<Long> warehouseIds = batches.stream().map(ReagentBatch::getWarehouseId).collect(Collectors.toSet());
        Map<Long, String> warehouseNameMap = new HashMap<>();
        if (!warehouseIds.isEmpty()) {
            List<Warehouse> warehouses = warehouseMapper.selectBatchIds(warehouseIds);
            for (Warehouse wh : warehouses) warehouseNameMap.put(wh.getId(), wh.getName());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ReagentBatch batch : batches) {
            Map<String, Object> row = new LinkedHashMap<>();
            ReagentCatalog cat = catalogMap.get(batch.getReagentId());
            row.put("id", batch.getId());
            row.put("batchNo", batch.getBatchNo());
            row.put("reagentId", batch.getReagentId());
            row.put("reagentName", cat != null ? cat.getGenericName() : null);
            row.put("hisCode", cat != null ? cat.getHisCode() : null);
            row.put("genericName", cat != null ? cat.getGenericName() : null);
            row.put("specification", cat != null ? cat.getSpecification() : null);
            row.put("manufacturer", cat != null ? cat.getManufacturer() : null);
            row.put("distributor", cat != null ? cat.getDistributor() : null);
            row.put("registrationCertNo", cat != null ? cat.getRegistrationCertNo() : null);
            row.put("insuranceCode", cat != null ? cat.getInsuranceCode() : null);
            row.put("isVolumeBased", cat != null ? cat.getIsVolumeBased() : 0);
            row.put("volumeType", cat != null ? cat.getVolumeType() : null);
            row.put("isChargeable", cat != null ? cat.getIsChargeable() : 0);
            row.put("manufactureDate", batch.getManufactureDate() != null ? batch.getManufactureDate().toString() : null);
            row.put("expiryDate", batch.getExpiryDate() != null ? batch.getExpiryDate().toString() : null);
            row.put("stock", batch.getStock());
            row.put("unitPrice", batch.getUnitPrice());
            row.put("amount", batch.getStock() != null && batch.getUnitPrice() != null
                    ? batch.getStock().multiply(batch.getUnitPrice()) : BigDecimal.ZERO);
            row.put("inboundDate", batch.getCreatedAt() != null ? batch.getCreatedAt().toLocalDate().toString() : null);
            row.put("warehouseId", batch.getWarehouseId());
            row.put("warehouseName", warehouseNameMap.getOrDefault(batch.getWarehouseId(), ""));
            row.put("udiDi", batch.getUdiDi());
            result.add(row);
        }
        return result;
    }

    @Override
    public Map<String, Long> getStats(Long warehouseId, Integer level) {
        Map<String, Long> stats = new LinkedHashMap<>();

        // 如果指定了 warehouse level，先查出该层级的所有仓库 ID
        final Set<Long> levelWarehouseIds;
        if (level != null) {
            List<Warehouse> levelWarehouses = warehouseMapper.selectList(
                    new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getLevel, level));
            levelWarehouseIds = levelWarehouses.stream().map(Warehouse::getId).collect(Collectors.toSet());
        } else {
            levelWarehouseIds = null;
        }

        // 构建仓库过滤条件
        java.util.function.Consumer<LambdaQueryWrapper<ReagentBatch>> applyWarehouseFilter = qw -> {
            if (warehouseId != null) qw.eq(ReagentBatch::getWarehouseId, warehouseId);
            if (levelWarehouseIds != null && !levelWarehouseIds.isEmpty()) qw.in(ReagentBatch::getWarehouseId, levelWarehouseIds);
        };

        // total: 符合条件的批次中库存>0的记录数
        LambdaQueryWrapper<ReagentBatch> totalQw = new LambdaQueryWrapper<ReagentBatch>().gt(ReagentBatch::getStock, BigDecimal.ZERO);
        applyWarehouseFilter.accept(totalQw);
        stats.put("total", batchMapper.selectCount(totalQw));
        stats.put("expiring30", batchMapper.selectCount(new LambdaQueryWrapper<ReagentBatch>().le(ReagentBatch::getExpiryDate, LocalDate.now().plusDays(30)).gt(ReagentBatch::getStock, BigDecimal.ZERO)));
        stats.put("expiring90", batchMapper.selectCount(new LambdaQueryWrapper<ReagentBatch>().le(ReagentBatch::getExpiryDate, LocalDate.now().plusDays(90)).gt(ReagentBatch::getStock, BigDecimal.ZERO)));

        // todayInbound: count of batches received today (created_at on reagent_batch)
        try {
            stats.put("todayInbound", batchMapper.selectCount(new LambdaQueryWrapper<ReagentBatch>()
                    .ge(ReagentBatch::getCreatedAt, LocalDate.now().atStartOfDay())));
        } catch (Exception e) { stats.put("todayInbound", 0L); }

        // todayOutbound: count of consumption records created today (JDBC to avoid cyclic dep)
        try {
            Long todayOut = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM consumption_record WHERE record_type = ? AND created_at >= ?",
                    Long.class, "CONSUME", LocalDate.now().atStartOfDay());
            stats.put("todayOutbound", todayOut != null ? todayOut : 0L);
        } catch (Exception e) { stats.put("todayOutbound", 0L); }

        // deptTotal: total batch records in dept warehouses (warehouse level=2)
        try {
            LambdaQueryWrapper<Warehouse> deptQw = new LambdaQueryWrapper<>();
            deptQw.eq(Warehouse::getLevel, 2);
            List<Warehouse> deptWarehouses = warehouseMapper.selectList(deptQw);
            Set<Long> deptWarehouseIds = deptWarehouses.stream().map(Warehouse::getId).collect(Collectors.toSet());
            long deptTotal = 0;
            if (!deptWarehouseIds.isEmpty()) {
                deptTotal = batchMapper.selectCount(new LambdaQueryWrapper<ReagentBatch>()
                        .in(ReagentBatch::getWarehouseId, deptWarehouseIds)
                        .gt(ReagentBatch::getStock, BigDecimal.ZERO));
            }
            stats.put("deptTotal", deptTotal);
        } catch (Exception e) { stats.put("deptTotal", 0L); }

        // deptDispatched: count of device_batch_load records created today (representing transfers to dept)
        try {
            stats.put("deptDispatched", loadMapper.selectCount(new LambdaQueryWrapper<DeviceBatchLoad>()
                    .ge(DeviceBatchLoad::getCreatedAt, LocalDate.now().atStartOfDay())));
        } catch (Exception e) { stats.put("deptDispatched", 0L); }

        // lowStock: count of batches where quantity < 10 (safety threshold)
        try {
            stats.put("lowStock", batchMapper.selectCount(new LambdaQueryWrapper<ReagentBatch>()
                    .lt(ReagentBatch::getStock, new BigDecimal("10"))
                    .gt(ReagentBatch::getStock, BigDecimal.ZERO)));
        } catch (Exception e) { stats.put("lowStock", 0L); }

        // pendingRequisitions: count of purchase_requisition with status PENDING_REVIEW
        try {
            Long pending = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM purchase_requisition WHERE status = ?", Long.class, "PENDING_REVIEW");
            stats.put("pendingRequisitions", pending != null ? pending : 0L);
        } catch (Exception e) { stats.put("pendingRequisitions", 0L); }

        // inTransit: 在途库存 = 拣货发货后尚未签收的数量
        try {
            String inTransitSql = "SELECT COALESCE(SUM(cr.quantity), 0) FROM consumption_record cr "
                    + "INNER JOIN purchase_requisition pr ON cr.requisition_id = pr.id "
                    + "WHERE cr.record_type = 'TRANSFER' AND pr.status IN ('SHIPPED', 'PARTIAL_FULFILLED') AND NOT EXISTS (SELECT 1 FROM requisition_transfer_receipt receipt WHERE receipt.transfer_record_id=cr.id)";
            BigDecimal inTransitRaw = jdbcTemplate.queryForObject(inTransitSql, BigDecimal.class);
            stats.put("inTransit", inTransitRaw != null ? inTransitRaw.longValue() : 0L);
        } catch (Exception e) { stats.put("inTransit", 0L); }

        return stats;
    }

    @Override
    public void transferBatch(Long batchId, Long targetWarehouseId, BigDecimal qty, String urgency, String reason) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(40001, "调拨数量必须大于 0");
        }
        Warehouse targetWarehouse = warehouseMapper.selectById(targetWarehouseId);
        if (targetWarehouse == null || !Objects.equals(targetWarehouse.getStatus(), 1)) {
            throw new BizException(40400, "目标仓库不存在或已停用");
        }

        // 1. 扣减源仓库库存（乐观锁重试）
        ReagentBatch sourceBatch = deductSourceBatchStock(batchId, qty);
        if (Objects.equals(sourceBatch.getWarehouseId(), targetWarehouseId)) {
            throw new BizException(40001, "源仓库与目标仓库不能相同");
        }

        // 2. 目标仓库：查找同批次号已有记录并累加，否则新建（乐观锁重试）
        Long targetBatchId = upsertTargetBatch(sourceBatch, targetWarehouseId, qty);

        // 3. 写入 TRANSFER consumption_record 审计追踪（使用 JdbcTemplate 避免模块循环依赖）
        Long recordId = IdGenerator.nextId();
        events.publishEvent(new com.zlth.ivd.common.event.InventoryTransferRecordedEvent(recordId,sourceBatch.getId(),
                sourceBatch.getOriginPurchaseOrderItemId(),sourceBatch.getSettlementMode(),sourceBatch.getBatchNo(),sourceBatch.getReagentId(),
                sourceBatch.getMaterialType()!=null?sourceBatch.getMaterialType().name():null,qty,sourceBatch.getUnit(),sourceBatch.getUnitPrice(),
                sourceBatch.getUnitPrice()!=null?sourceBatch.getUnitPrice().multiply(qty):null,targetWarehouseId,sourceBatch.getWarehouseId(),reason));

        // 4. 唯一码全流程流转：将调拨的唯一码关联到目标批次
        int codeCount = qty.intValue();
        if (codeCount > 0) {
            uniqueCodeService.transferUniqueCodes(batchId, targetBatchId, codeCount);
        }

        log.info("批次调拨: sourceBatchId={}, targetWarehouseId={}, qty={}, urgency={}, reason={}, auditId={}", batchId, targetWarehouseId, qty, urgency, reason, recordId);
    }

    /**
     * 扣减源仓库库存（乐观锁重试，最多 3 次）
     */
    private ReagentBatch deductSourceBatchStock(Long batchId, BigDecimal qty) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            ReagentBatch sourceBatch = batchMapper.selectById(batchId);
            if (sourceBatch == null) throw new BizException(40400, "批次不存在");
            if (sourceBatch.getUdiStatus() != UdiStatusEnum.IN_STOCK) {
                throw new BizException(40001, "仅在库批次允许调拨");
            }
            if (sourceBatch.getStock().compareTo(qty) < 0) throw new BizException(40001, "库存不足");

            sourceBatch.setStock(sourceBatch.getStock().subtract(qty));
            boolean depleted = sourceBatch.getStock().compareTo(BigDecimal.ZERO) <= 0;

            int updated = batchMapper.updateById(sourceBatch);
            if (updated > 0) {
                if (depleted) {
                    udiStateMachine.transition(sourceBatch.getId(), sourceBatch.getBatchNo(),
                            UdiStatusEnum.IN_STOCK, UdiStatusEnum.TRANSFERRED, currentOperator());
                    sourceBatch.setUdiStatus(UdiStatusEnum.TRANSFERRED);
                }
                return sourceBatch;
            }
            if (i == maxRetries - 1) {
                throw new BizException(50000, "调拨扣减失败：超过最大重试次数");
            }
            log.warn("调拨扣减乐观锁冲突，正在重试 ({}/{})", i + 1, maxRetries);
        }
        throw new BizException(50000, "调拨扣减失败：超过最大重试次数");
    }

    /**
     * 目标仓库累加/新建批次（乐观锁重试累加分支，最多 3 次）
     */
    private Long upsertTargetBatch(ReagentBatch sourceBatch, Long targetWarehouseId, BigDecimal qty) {
        return stockOperations.receiveIntoWarehouse(sourceBatch,targetWarehouseId,qty).getId();
    }

    private String currentOperator() {
        Long userId = SecurityUtils.getCurrentUserIdOrNull();
        return userId != null ? userId.toString() : SecurityUtils.getCurrentUsername();
    }

    @Override
    public Map<String, Object> traceUdi(String udiDi, String lotNo) {
        return new TraceQueryService(jdbcTemplate).trace(udiDi,lotNo);
    }

    @Override
    public Map<String, Object> getReagentUdiData(Long reagentId) {
        // 1. 试剂目录信息
        ReagentCatalog catalog = catalogMapper.selectById(reagentId);
        if (catalog == null) throw new BizException(40400, "试剂不存在: " + reagentId);

        // 2. 该试剂的所有批次
        List<ReagentBatch> batches = batchMapper.selectList(
                new LambdaQueryWrapper<ReagentBatch>()
                        .eq(ReagentBatch::getReagentId, reagentId)
                        .orderByDesc(ReagentBatch::getCreatedAt));

        // 3. 组装响应
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reagentId", catalog.getId());
        result.put("reagentName", catalog.getGenericName());
        result.put("manufacturer", catalog.getManufacturer());
        result.put("registrationCertNo", catalog.getRegistrationCertNo());
        result.put("hisCode", catalog.getHisCode());
        result.put("spec", catalog.getSpecification());
        result.put("insuranceCode", catalog.getInsuranceCode());
        result.put("nmpaCert", catalog.getRegistrationCertNo());

        // 汇总批次总库存（仅一级库/中心库，且仅统计有效在库状态）
        BigDecimal totalStock = BigDecimal.ZERO;
        BigDecimal inTransitStock = BigDecimal.ZERO;
        // 收集一级库 warehouse ID
        Set<Long> centralWarehouseIds = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getLevel, 1))
                .stream().map(Warehouse::getId).collect(Collectors.toSet());

        // 中心库有效状态：仅统计物理在库的批次
        Set<String> activeStatuses = Set.of("IN_STOCK", "RECEIVED", "PENDING_UDI");

        List<Map<String, Object>> batchList = new ArrayList<>();
        for (ReagentBatch b : batches) {
            String status = b.getUdiStatus() != null ? b.getUdiStatus().name() : null;
            // 跳过非活跃状态（IN_USE/CONSUMED/TRANSFERRED/RETURNED/EXPIRED/RECALLED/BACK_SUPPLIER/SCRAPPED/DESTROYED）
            if (status == null || !activeStatuses.contains(status)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("batchId", b.getId());
            item.put("batchNo", b.getBatchNo());
            item.put("udiDi", b.getUdiDi());
            item.put("udiPi", b.getUdiPi());
            item.put("stock", b.getStock());
            item.put("unit", b.getUnit());
            item.put("expiryDate", b.getExpiryDate());
            item.put("manufactureDate", b.getManufactureDate());
            item.put("status", status);
            item.put("statusLabel", b.getUdiStatus() != null ? b.getUdiStatus().getLabel() : null);
            item.put("warehouseId", b.getWarehouseId());
            item.put("locationCode", b.getLocationCode());
            batchList.add(item);
            if (b.getStock() != null) {
                if (b.getWarehouseId() != null && centralWarehouseIds.contains(b.getWarehouseId())) {
                    totalStock = totalStock.add(b.getStock());
                }
            }
        }
        result.put("batches", batchList);
        result.put("batchCount", batchList.size());
        result.put("totalStock", totalStock);

        // 4. 是否启用唯一码管控
        boolean enableUniqueCode = catalog.getEnableUniqueCode() != null && catalog.getEnableUniqueCode() == 1;
        result.put("enableUniqueCode", enableUniqueCode);

        // 5. 在途库存：拣货发货后尚未签收的数量（TRANSFER 记录关联的申领单未 FULFILLED）
        try {
            String inTransitSql = "SELECT COALESCE(SUM(cr.quantity), 0) FROM consumption_record cr "
                    + "INNER JOIN purchase_requisition pr ON cr.requisition_id = pr.id "
                    + "WHERE cr.reagent_id = ? AND cr.record_type = 'TRANSFER' "
                    + "AND pr.status IN ('SHIPPED', 'PARTIAL_FULFILLED') AND NOT EXISTS (SELECT 1 FROM requisition_transfer_receipt receipt WHERE receipt.transfer_record_id=cr.id)";
            BigDecimal raw = jdbcTemplate.queryForObject(inTransitSql, BigDecimal.class, reagentId);
            inTransitStock = raw != null ? raw : BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("查询在途库存失败: reagentId={}, error={}", reagentId, e.getMessage());
            inTransitStock = BigDecimal.ZERO;
        }
        result.put("inTransitStock", inTransitStock);

        // 6. 该试剂的所有唯一码（仅当启用唯一码管控时才查询）
        if (enableUniqueCode) {
            // 6a. 在库唯一码数：仅统计中心库（一级库）中 IN_STOCK 的唯一码
            int inStockCount = 0;
            try {
                String inStockSql = "SELECT COUNT(*) FROM udi_unique_code uc"
                        + " INNER JOIN reagent_batch rb ON uc.batch_id = rb.id"
                        + " INNER JOIN warehouse w ON rb.warehouse_id = w.id"
                        + " WHERE uc.reagent_id = ? AND uc.status = 'IN_STOCK' AND w.level = 1";
                Integer raw = jdbcTemplate.queryForObject(inStockSql, Integer.class, reagentId);
                inStockCount = raw != null ? raw : 0;
            } catch (Exception e) {
                log.warn("查询在库唯一码失败: reagentId={}, error={}", reagentId, e.getMessage());
            }

            // 6b. 已消耗唯一码数
            int consumedCount = 0;
            try {
                String consumedSql = "SELECT COUNT(*) FROM udi_unique_code WHERE reagent_id = ? AND status = 'CONSUMED'";
                Integer raw = jdbcTemplate.queryForObject(consumedSql, Integer.class, reagentId);
                consumedCount = raw != null ? raw : 0;
            } catch (Exception e) {
                log.warn("查询已消耗唯一码失败: reagentId={}, error={}", reagentId, e.getMessage());
            }

            // 6c. 已调拨唯一码数
            int transferredCount = 0;
            try {
                String transferredSql = "SELECT COUNT(*) FROM udi_unique_code WHERE reagent_id = ? AND status = 'TRANSFERRED'";
                Integer raw = jdbcTemplate.queryForObject(transferredSql, Integer.class, reagentId);
                transferredCount = raw != null ? raw : 0;
            } catch (Exception e) {
                log.warn("查询已调拨唯一码失败: reagentId={}, error={}", reagentId, e.getMessage());
            }

            // 6d. 在库唯一码列表（仅中心库 IN_STOCK 的，供前端展示）
            List<Map<String, Object>> codeList = new ArrayList<>();
            try {
                String codeListSql = "SELECT uc.id, uc.unique_code, uc.batch_id, uc.batch_no, uc.udi_di, uc.status"
                        + " FROM udi_unique_code uc"
                        + " INNER JOIN reagent_batch rb ON uc.batch_id = rb.id"
                        + " INNER JOIN warehouse w ON rb.warehouse_id = w.id"
                        + " WHERE uc.reagent_id = ? AND uc.status = 'IN_STOCK' AND w.level = 1"
                        + " ORDER BY uc.unique_code ASC";
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(codeListSql, reagentId);
                for (Map<String, Object> row : rows) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.get("id"));
                    item.put("uniqueCode", row.get("unique_code"));
                    item.put("batchId", row.get("batch_id"));
                    item.put("batchNo", row.get("batch_no"));
                    item.put("udiDi", row.get("udi_di"));
                    item.put("status", row.get("status"));
                    codeList.add(item);
                }
            } catch (Exception e) {
                log.warn("查询在库唯一码列表失败: reagentId={}, error={}", reagentId, e.getMessage());
            }

            int uniqueCodeCount = inStockCount + consumedCount + transferredCount;
            result.put("uniqueCodes", codeList);
            result.put("uniqueCodeCount", uniqueCodeCount);
            result.put("inStockCodeCount", inStockCount);
            result.put("consumedCodeCount", consumedCount);
            result.put("transferredCodeCount", transferredCount);
        } else {
            // 未启用唯一码管控：返回空数据
            result.put("uniqueCodes", new ArrayList<>());
            result.put("uniqueCodeCount", 0);
            result.put("inStockCodeCount", 0);
            result.put("consumedCodeCount", 0);
            result.put("transferredCodeCount", 0);
        }
        return result;
    }

    @Override
    public Map<String, Object> getUniqueCodeTrace(String uniqueCode) {
        UdiUniqueCode code = uniqueCodeMapper.selectOne(
                new LambdaQueryWrapper<UdiUniqueCode>()
                        .eq(UdiUniqueCode::getUniqueCode, uniqueCode));
        if (code == null) throw new BizException(40400, "唯一码不存在: " + uniqueCode);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uniqueCode", code.getUniqueCode());
        result.put("status", code.getStatus());
        result.put("batchId", code.getBatchId());
        result.put("batchNo", code.getBatchNo());
        result.put("udiDi", code.getUdiDi());
        result.put("createdAt", code.getCreatedAt());
        result.put("updatedAt", code.getUpdatedAt());

        // 补全试剂名称
        if (code.getReagentId() != null) {
            ReagentCatalog catalog = catalogMapper.selectById(code.getReagentId());
            if (catalog != null) {
                result.put("reagentId", catalog.getId());
                result.put("reagentName", catalog.getGenericName());
                result.put("manufacturer", catalog.getManufacturer());
            }
        }

        // 补全批次库存信息
        if (code.getBatchId() != null) {
            ReagentBatch batch = batchMapper.selectById(code.getBatchId());
            if (batch != null) {
                result.put("stock", batch.getStock());
                result.put("warehouseId", batch.getWarehouseId());
                result.put("expiryDate", batch.getExpiryDate());
            }
        }

        return result;
    }

    @Override
    public List<PatientAssociation> getTracePatients(String batchNo) {
        LambdaQueryWrapper<PatientAssociation> qw = new LambdaQueryWrapper<>();
        qw.eq(PatientAssociation::getBatchNo, batchNo).orderByDesc(PatientAssociation::getTestTime);
        return patientAssociationMapper.selectList(qw);
    }

    @Override
    public PageVO<Map<String, Object>> listDeptInventory(int page, int pageSize, Long deptId, String keyword,
                                                          String hisCode, String reagentName, String distributor, String manufacturer, String registrationCertNo) {
        // 0. 如果提供了试剂目录级筛选条件，先查出匹配的 reagentId 集合
        Set<Long> catalogFilteredIds = null;
        boolean hasCatalogFilter = hisCode != null || reagentName != null || distributor != null
                || manufacturer != null || registrationCertNo != null;
        if (hasCatalogFilter) {
            LambdaQueryWrapper<ReagentCatalog> cqw = new LambdaQueryWrapper<>();
            if (hisCode != null) cqw.like(ReagentCatalog::getHisCode, hisCode);
            if (reagentName != null) cqw.like(ReagentCatalog::getGenericName, reagentName);
            if (distributor != null) cqw.like(ReagentCatalog::getDistributor, distributor);
            if (manufacturer != null) cqw.like(ReagentCatalog::getManufacturer, manufacturer);
            if (registrationCertNo != null) cqw.like(ReagentCatalog::getRegistrationCertNo, registrationCertNo);
            List<ReagentCatalog> matchedCatalogs = catalogMapper.selectList(cqw);
            catalogFilteredIds = matchedCatalogs.stream().map(ReagentCatalog::getId).collect(Collectors.toSet());
            if (catalogFilteredIds.isEmpty()) {
                return PageVO.empty(page, pageSize);
            }
        }

        // 1. 找出科室级仓库（level=2）及下属组库（level=3）
        List<Long> deptWarehouseIds = new ArrayList<>();
        List<Warehouse> allWarehouses = warehouseMapper.selectList(null);

        // 构建仓库父子关系
        Map<Long, List<Warehouse>> childrenMap = new HashMap<>();
        for (Warehouse wh : allWarehouses) {
            if (wh.getParentId() != null) {
                childrenMap.computeIfAbsent(wh.getParentId(), k -> new ArrayList<>()).add(wh);
            }
        }

        if (deptId != null) {
            // 指定科室：包含科室本身 + 下属组库
            deptWarehouseIds.add(deptId);
            collectChildIds(deptId, childrenMap, deptWarehouseIds);
        } else {
            // 全部科室：所有 level=2 的仓库 + 各自下属组库
            for (Warehouse wh : allWarehouses) {
                if (wh.getLevel() != null && wh.getLevel() == 2) {
                    deptWarehouseIds.add(wh.getId());
                    collectChildIds(wh.getId(), childrenMap, deptWarehouseIds);
                }
            }
        }

        if (deptWarehouseIds.isEmpty()) {
            return PageVO.empty(page, pageSize);
        }

        // 2. 查询科室库下的批次
        LambdaQueryWrapper<ReagentBatch> qw = new LambdaQueryWrapper<>();
        qw.in(ReagentBatch::getWarehouseId, deptWarehouseIds);
        qw.gt(ReagentBatch::getStock, BigDecimal.ZERO);
        if (catalogFilteredIds != null) qw.in(ReagentBatch::getReagentId, catalogFilteredIds);
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(ReagentBatch::getBatchNo, keyword).or().like(ReagentBatch::getUdiDi, keyword));
        }
        qw.orderByAsc(ReagentBatch::getExpiryDate);

        Page<ReagentBatch> p = batchMapper.selectPage(new Page<>(page, pageSize), qw);

        // 3. 批量查试剂名称
        Set<Long> reagentIds = p.getRecords().stream()
                .map(ReagentBatch::getReagentId).collect(Collectors.toSet());
        Map<Long, ReagentCatalog> catalogMap = new HashMap<>();
        if (!reagentIds.isEmpty()) {
            List<ReagentCatalog> catalogs = catalogMapper.selectBatchIds(reagentIds);
            for (ReagentCatalog c : catalogs) {
                catalogMap.put(c.getId(), c);
            }
        }

        // 4. 组装返回数据
        List<Map<String, Object>> records = new ArrayList<>();
        for (ReagentBatch batch : p.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            ReagentCatalog catalog = catalogMap.get(batch.getReagentId());
            row.put("id", batch.getId());
            row.put("reagentId", batch.getReagentId());
            row.put("reagentName", catalog != null ? catalog.getGenericName() : "试剂#" + batch.getReagentId());
            row.put("hisCode", catalog != null ? catalog.getHisCode() : null);
            row.put("genericName", catalog != null ? catalog.getGenericName() : null);
            row.put("specification", catalog != null ? catalog.getSpecification() : null);
            row.put("distributor", catalog != null ? catalog.getDistributor() : null);
            row.put("manufacturer", catalog != null ? catalog.getManufacturer() : null);
            row.put("registrationCertNo", catalog != null ? catalog.getRegistrationCertNo() : null);
            row.put("insuranceCode", catalog != null ? catalog.getInsuranceCode() : null);
            row.put("isVolumeBased", catalog != null ? catalog.getIsVolumeBased() : 0);
            row.put("volumeType", catalog != null ? catalog.getVolumeType() : null);
            row.put("isChargeable", catalog != null ? catalog.getIsChargeable() : 0);
            row.put("batchNo", batch.getBatchNo());
            row.put("udiDi", batch.getUdiDi());
            row.put("manufactureDate", batch.getManufactureDate() != null ? batch.getManufactureDate().toString() : null);
            row.put("expiryDate", batch.getExpiryDate() != null ? batch.getExpiryDate().toString() : null);
            row.put("stock", batch.getStock());
            row.put("unitPrice", batch.getUnitPrice());
            row.put("amount", batch.getStock() != null && batch.getUnitPrice() != null
                    ? batch.getStock().multiply(batch.getUnitPrice()) : BigDecimal.ZERO);
            row.put("inboundDate", batch.getCreatedAt() != null ? batch.getCreatedAt().toLocalDate().toString() : null);
            // 安全库存（待二期规则引擎接入后动态计算）
            row.put("safetyStock", BigDecimal.ZERO);
            // 库位（仓库名称）
            Warehouse wh = allWarehouses.stream()
                    .filter(w -> w.getId().equals(batch.getWarehouseId())).findFirst().orElse(null);
            row.put("location", wh != null ? wh.getName() : "");
            // 可支撑天数 = 当前库存 / 日均消耗
            BigDecimal dailyRate = BigDecimal.ONE;
            long supportDays = dailyRate.compareTo(BigDecimal.ZERO) > 0
                    ? batch.getStock().divide(dailyRate, 0, RoundingMode.DOWN).longValue()
                    : batch.getStock().longValue();
            row.put("supportDays", supportDays);
            records.add(row);
        }

        return new PageVO<>(p.getTotal(), page, pageSize, records);
    }

    /** 递归收集子仓库ID */
    private void collectChildIds(Long parentId, Map<Long, List<Warehouse>> childrenMap, List<Long> result) {
        List<Warehouse> children = childrenMap.get(parentId);
        if (children != null) {
            for (Warehouse child : children) {
                result.add(child.getId());
                collectChildIds(child.getId(), childrenMap, result);
            }
        }
    }
}
