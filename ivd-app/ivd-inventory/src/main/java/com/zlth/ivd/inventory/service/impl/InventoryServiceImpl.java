package com.zlth.ivd.inventory.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.catalog.mapper.ReagentCatalogMapper;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.enums.UdiStatusEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.inventory.entity.*;
import com.zlth.ivd.inventory.vo.ReagentBatchVO;
import com.zlth.ivd.inventory.mapper.*;
import com.zlth.ivd.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    public PageVO<ReagentBatchVO> listBatches(int page, int pageSize, String keyword, String status, Long warehouseId,
                                               String hisCode, String reagentName, String distributor, String manufacturer, String registrationCertNo) {
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
            vo.setHisCode(cat != null ? cat.getHisCode() : null);
            vo.setDistributor(cat != null ? cat.getDistributor() : null);
            vo.setManufacturer(cat != null ? cat.getManufacturer() : null);
            vo.setRegistrationCertNo(cat != null ? cat.getRegistrationCertNo() : null);
            vo.setMaterialType(batch.getMaterialType());
            vo.setUdiDi(batch.getUdiDi()); vo.setUdiPi(batch.getUdiPi());
            vo.setManufactureDate(batch.getManufactureDate());
            vo.setExpiryDate(batch.getExpiryDate());
            vo.setStock(batch.getStock()); vo.setUnit(batch.getUnit());
            vo.setUnitPrice(batch.getUnitPrice());
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
    public List<ReagentBatch> getFefoRecommendations(Long reagentId, Long warehouseId) {
        // FEFO: 按效期升序排列（最早到期优先出库）；reagentId 为空时返回该仓库下所有试剂的FEFO推荐
        LambdaQueryWrapper<ReagentBatch> qw = new LambdaQueryWrapper<>();
        qw.eq(reagentId != null, ReagentBatch::getReagentId, reagentId)
          .eq(warehouseId != null, ReagentBatch::getWarehouseId, warehouseId)
          .eq(ReagentBatch::getUdiStatus, UdiStatusEnum.IN_STOCK)
          .gt(ReagentBatch::getStock, BigDecimal.ZERO)
          .orderByAsc(ReagentBatch::getExpiryDate);
        return batchMapper.selectList(qw);
    }

    @Override
    public List<ReagentBatch> getExpiryAlerts(int days, Long warehouseId) {
        LocalDate threshold = LocalDate.now().plusDays(days);
        LambdaQueryWrapper<ReagentBatch> qw = new LambdaQueryWrapper<>();
        qw.le(ReagentBatch::getExpiryDate, threshold)
          .eq(warehouseId != null, ReagentBatch::getWarehouseId, warehouseId)
          .gt(ReagentBatch::getStock, BigDecimal.ZERO)
          .orderByAsc(ReagentBatch::getExpiryDate);
        return batchMapper.selectList(qw);
    }

    @Override
    public Map<String, Long> getStats(Long warehouseId) {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", batchMapper.selectCount(new LambdaQueryWrapper<ReagentBatch>().eq(warehouseId != null, ReagentBatch::getWarehouseId, warehouseId).gt(ReagentBatch::getStock, BigDecimal.ZERO)));
        stats.put("expiring30", batchMapper.selectCount(new LambdaQueryWrapper<ReagentBatch>().le(ReagentBatch::getExpiryDate, LocalDate.now().plusDays(30)).gt(ReagentBatch::getStock, BigDecimal.ZERO)));
        stats.put("expiring90", batchMapper.selectCount(new LambdaQueryWrapper<ReagentBatch>().le(ReagentBatch::getExpiryDate, LocalDate.now().plusDays(90)).gt(ReagentBatch::getStock, BigDecimal.ZERO)));
        return stats;
    }

    @Override
    public void transferBatch(Long batchId, Long targetWarehouseId, BigDecimal qty, String urgency) {
        // 1. 扣减源仓库库存（乐观锁重试）
        ReagentBatch sourceBatch = deductSourceBatchStock(batchId, qty);

        // 2. 目标仓库：查找同批次号已有记录并累加，否则新建（乐观锁重试）
        upsertTargetBatch(sourceBatch, targetWarehouseId, qty);

        log.info("批次调拨: sourceBatchId={}, targetWarehouseId={}, qty={}, urgency={}", batchId, targetWarehouseId, qty, urgency);
    }

    /**
     * 扣减源仓库库存（乐观锁重试，最多 3 次）
     */
    private ReagentBatch deductSourceBatchStock(Long batchId, BigDecimal qty) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            ReagentBatch sourceBatch = batchMapper.selectById(batchId);
            if (sourceBatch == null) throw new BizException(40400, "批次不存在");
            if (sourceBatch.getStock().compareTo(qty) < 0) throw new BizException(40001, "库存不足");

            sourceBatch.setStock(sourceBatch.getStock().subtract(qty));
            if (sourceBatch.getStock().compareTo(BigDecimal.ZERO) <= 0) {
                sourceBatch.setUdiStatus(UdiStatusEnum.TRANSFERRED);
            }

            try {
                batchMapper.updateById(sourceBatch);
                return sourceBatch;
            } catch (MybatisPlusException e) {
                if (i == maxRetries - 1) throw e;
                log.warn("调拨扣减乐观锁冲突，正在重试 ({}/{})", i + 1, maxRetries);
            }
        }
        throw new BizException(50000, "调拨扣减失败：超过最大重试次数");
    }

    /**
     * 目标仓库累加/新建批次（乐观锁重试累加分支，最多 3 次）
     */
    private void upsertTargetBatch(ReagentBatch sourceBatch, Long targetWarehouseId, BigDecimal qty) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            LambdaQueryWrapper<ReagentBatch> targetQw = new LambdaQueryWrapper<>();
            targetQw.eq(ReagentBatch::getWarehouseId, targetWarehouseId)
                    .eq(ReagentBatch::getBatchNo, sourceBatch.getBatchNo());
            ReagentBatch targetBatch = batchMapper.selectOne(targetQw);

            if (targetBatch != null) {
                targetBatch.setStock(targetBatch.getStock().add(qty));
                try {
                    batchMapper.updateById(targetBatch);
                    return;
                } catch (MybatisPlusException e) {
                    if (i == maxRetries - 1) throw e;
                    log.warn("调拨入库乐观锁冲突，正在重试 ({}/{})", i + 1, maxRetries);
                }
            } else {
                ReagentBatch newBatch = new ReagentBatch();
                newBatch.setId(IdUtil.getSnowflakeNextId());
                newBatch.setBatchNo(sourceBatch.getBatchNo());
                newBatch.setReagentId(sourceBatch.getReagentId());
                newBatch.setMaterialType(sourceBatch.getMaterialType());
                newBatch.setUdiDi(sourceBatch.getUdiDi());
                newBatch.setUdiPi(sourceBatch.getUdiPi());
                newBatch.setManufactureDate(sourceBatch.getManufactureDate());
                newBatch.setExpiryDate(sourceBatch.getExpiryDate());
                newBatch.setStock(qty);
                newBatch.setUnit(sourceBatch.getUnit());
                newBatch.setUnitPrice(sourceBatch.getUnitPrice());
                newBatch.setAvgCost(sourceBatch.getAvgCost());
                newBatch.setUdiStatus(UdiStatusEnum.IN_STOCK);
                newBatch.setWarehouseId(targetWarehouseId);
                newBatch.setLocationCode(sourceBatch.getLocationCode());
                batchMapper.insert(newBatch);
                return; // INSERT 无乐观锁冲突，直接返回
            }
        }
        throw new BizException(50000, "调拨入库失败：超过最大重试次数");
    }

    @Override
    public Map<String, Object> traceUdi(String udiDi, String lotNo) {
        LambdaQueryWrapper<ReagentBatch> qw = new LambdaQueryWrapper<>();
        qw.eq(ReagentBatch::getUdiDi, udiDi);
        if (lotNo != null) qw.eq(ReagentBatch::getBatchNo, lotNo);
        ReagentBatch batch = batchMapper.selectOne(qw);
        if (batch == null) throw new BizException(40400, "未找到该 UDI 的批次信息");
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("batchId", batch.getId());
        trace.put("batchNo", batch.getBatchNo());
        trace.put("udiDi", batch.getUdiDi());
        trace.put("reagentId", batch.getReagentId());
        trace.put("status", batch.getUdiStatus().name());
        trace.put("statusLabel", batch.getUdiStatus().getLabel());
        trace.put("stock", batch.getStock());
        trace.put("expiryDate", batch.getExpiryDate());
        trace.put("warehouseId", batch.getWarehouseId());
        // Timeline: purchase → inbound → central warehouse → dept → consumption
        List<Map<String, String>> timeline = new ArrayList<>();
        timeline.add(Map.of("node", "采购订单", "status", "completed"));
        timeline.add(Map.of("node", "扫码验收", "status", "completed"));
        timeline.add(Map.of("node", "一级库入库", "status", "completed"));
        timeline.add(Map.of("node", "调拨至科室", "status", batch.getUdiStatus().ordinal() >= UdiStatusEnum.TRANSFERRED.ordinal() ? "completed" : "pending"));
        timeline.add(Map.of("node", "扫码上机", "status", batch.getUdiStatus().ordinal() >= UdiStatusEnum.IN_USE.ordinal() ? "completed" : "pending"));
        timeline.add(Map.of("node", "消耗核销", "status", batch.getUdiStatus() == UdiStatusEnum.CONSUMED ? "completed" : "pending"));
        trace.put("timeline", timeline);
        return trace;
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
            row.put("distributor", catalog != null ? catalog.getDistributor() : null);
            row.put("manufacturer", catalog != null ? catalog.getManufacturer() : null);
            row.put("registrationCertNo", catalog != null ? catalog.getRegistrationCertNo() : null);
            row.put("batchNo", batch.getBatchNo());
            row.put("udiDi", batch.getUdiDi());
            row.put("expiryDate", batch.getExpiryDate() != null ? batch.getExpiryDate().toString() : null);
            row.put("stock", batch.getStock());
            // 安全库存取自试剂目录
            row.put("safetyStock", catalog != null && catalog.getMinStockLevel() != null
                    ? catalog.getMinStockLevel() : BigDecimal.ZERO);
            // 库位（仓库名称）
            Warehouse wh = allWarehouses.stream()
                    .filter(w -> w.getId().equals(batch.getWarehouseId())).findFirst().orElse(null);
            row.put("location", wh != null ? wh.getName() : "");
            // 可支撑天数 = 当前库存 / 日均消耗（取安全库存/7 为估算日均消耗，避免除零）
            BigDecimal dailyRate = BigDecimal.ONE;
            if (catalog != null && catalog.getMinStockLevel() != null
                    && catalog.getMinStockLevel().compareTo(BigDecimal.ZERO) > 0) {
                dailyRate = catalog.getMinStockLevel().divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
            }
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
