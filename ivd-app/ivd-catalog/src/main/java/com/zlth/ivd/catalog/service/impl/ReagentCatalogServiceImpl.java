package com.zlth.ivd.catalog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlth.ivd.catalog.dto.CreateReagentRequest;
import com.zlth.ivd.catalog.dto.ReagentCatalogQuery;
import com.zlth.ivd.catalog.dto.ReviewRequest;
import com.zlth.ivd.catalog.dto.SubmitApprovalRequest;
import com.zlth.ivd.catalog.dto.UpdateReagentRequest;
import com.zlth.ivd.catalog.entity.CatalogApprovalLog;
import com.zlth.ivd.catalog.entity.FiveCodeMapping;
import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.catalog.entity.UdiBacklog;
import com.zlth.ivd.catalog.mapper.ReagentCatalogMapper;
import com.zlth.ivd.catalog.service.CatalogApprovalLogService;
import com.zlth.ivd.catalog.service.FiveCodeMappingService;
import com.zlth.ivd.catalog.service.ReagentCatalogService;
import com.zlth.ivd.catalog.service.UdiBacklogService;
import com.zlth.ivd.catalog.vo.FiveCodeMappingVO;
import com.zlth.ivd.catalog.vo.ReagentCatalogVO;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.enums.ApprovalStatusEnum;
import com.zlth.ivd.common.enums.DeviceBindModeEnum;
import com.zlth.ivd.common.enums.FiveCodeMappingStatusEnum;
import com.zlth.ivd.common.enums.MaterialTypeEnum;
import com.zlth.ivd.common.exception.ApprovalPendingException;
import com.zlth.ivd.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 试剂目录管理服务实现
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ReagentCatalogServiceImpl implements ReagentCatalogService {

    private final ReagentCatalogMapper catalogMapper;
    private final FiveCodeMappingService fiveCodeMappingService;
    private final CatalogApprovalLogService approvalLogService;
    private final UdiBacklogService udiBacklogService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ──────────────────────────────────────────────
    //  查询
    // ──────────────────────────────────────────────

    @Override
    public PageVO<ReagentCatalogVO> pageList(ReagentCatalogQuery query, int page, int pageSize) {
        Page<ReagentCatalog> p = new Page<>(page, Math.min(pageSize, 500));
        IPage<ReagentCatalog> iPage = catalogMapper.pageList(p, query);
        return PageVO.of(iPage, this::toVO);
    }

    @Override
    public ReagentCatalogVO getById(Long id) {
        ReagentCatalog entity = catalogMapper.selectById(id);
        if (entity == null) {
            throw new BizException(40007, "试剂不存在: id=" + id);
        }
        return toVO(entity);
    }

    // ──────────────────────────────────────────────
    //  新增
    // ──────────────────────────────────────────────

    @Override
    public ReagentCatalogVO create(CreateReagentRequest request) {
        // 1. hisCode 唯一性校验
        Long exists = catalogMapper.selectCount(
                new LambdaQueryWrapper<ReagentCatalog>()
                        .eq(ReagentCatalog::getHisCode, request.getHisCode())
        );
        if (exists != null && exists > 0) {
            throw new BizException(40001, "院内编码已存在: " + request.getHisCode());
        }

        // 2. 按物料类型校验条件必填字段
        MaterialTypeEnum materialType = request.getMaterialType();
        if (materialType.isUdiRequired() && isBlank(request.getUdiDi())) {
            throw new BizException(40002, "物料类型 [" + materialType.getLabel() + "] 要求填写 UDI-DI");
        }
        if (materialType.isRegistrationCertRequired() && isBlank(request.getRegistrationCertNo())) {
            throw new BizException(40003, "物料类型 [" + materialType.getLabel() + "] 要求填写注册证号");
        }
        if (materialType.isTestsPerKitRequired() && request.getTestsPerKit() == null) {
            throw new BizException(40004, "物料类型 [试剂] 要求填写标称测试数");
        }

        // 3. 构建实体
        ReagentCatalog entity = new ReagentCatalog();
        entity.setHisCode(request.getHisCode());
        entity.setGenericName(request.getGenericName());
        entity.setMaterialType(materialType);
        entity.setManufacturer(request.getManufacturer());
        entity.setDistributor(request.getDistributor());
        entity.setSpecification(request.getSpecification());
        entity.setUnit(request.getUnit());
        entity.setStorageCondition(request.getStorageCondition());
        entity.setTestGroups(request.getTestGroups());
        entity.setUdiDi(request.getUdiDi());
        entity.setGtin(request.getGtin());
        entity.setInsuranceCode(request.getInsuranceCode());
        entity.setRegistrationCertNo(request.getRegistrationCertNo());
        entity.setDetectionMethod(request.getDetectionMethod());
        entity.setTestsPerKit(request.getTestsPerKit());
        entity.setPricePerUnit(request.getPricePerUnit());
        entity.setStabilityDaysAfterOpen(request.getStabilityDaysAfterOpen());
        entity.setLeadTimeDays(request.getLeadTimeDays() != null ? request.getLeadTimeDays() : 7);
        entity.setDeviceBindMode(request.getDeviceBindMode() != null ? request.getDeviceBindMode() : DeviceBindModeEnum.FORCE);
        entity.setEnableUniqueCode(request.getEnableUniqueCode() != null ? request.getEnableUniqueCode() : 0);
        entity.setIsVolumeBased(request.getIsVolumeBased() != null ? request.getIsVolumeBased() : 0);
        entity.setVolumeType(request.getVolumeType());
        entity.setIsInsurancePayment(request.getIsInsurancePayment() != null ? request.getIsInsurancePayment() : 0);
        entity.setInsurancePaymentType(request.getInsurancePaymentType());
        entity.setIsChargeable(request.getIsChargeable() != null ? request.getIsChargeable() : 0);
        entity.setChargeCode(request.getChargeCode());
        entity.setApprovalStatus(ApprovalStatusEnum.DRAFT);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        // 4. 持久化
        catalogMapper.insert(entity);
        log.info("试剂已创建: id={}, hisCode={}, materialType={}", entity.getId(), entity.getHisCode(), materialType.getCode());

        // 5. 条件创建五码映射
        if (materialType.requiresFiveCodeMapping()) {
            fiveCodeMappingService.createMapping(
                    entity.getId(),
                    entity.getHisCode(),
                    entity.getUdiDi(),
                    entity.getGtin(),
                    entity.getInsuranceCode(),
                    entity.getRegistrationCertNo()
            );
        }

        return toVO(entity);
    }

    // ──────────────────────────────────────────────
    //  编辑
    // ──────────────────────────────────────────────

    @Override
    public ReagentCatalogVO update(Long id, UpdateReagentRequest request) {
        ReagentCatalog entity = catalogMapper.selectById(id);
        if (entity == null) {
            throw new BizException(40007, "试剂不存在: id=" + id);
        }

        // 追踪字段变更（用于审批日志）
        Map<String, Object> changes = new LinkedHashMap<>();

        applyIfChanged("genericName", entity.getGenericName(), request.getGenericName(), entity::setGenericName, changes);
        applyIfChanged("udiDi", entity.getUdiDi(), request.getUdiDi(), entity::setUdiDi, changes);
        applyIfChanged("gtin", entity.getGtin(), request.getGtin(), entity::setGtin, changes);
        applyIfChanged("insuranceCode", entity.getInsuranceCode(), request.getInsuranceCode(), entity::setInsuranceCode, changes);
        applyIfChanged("registrationCertNo", entity.getRegistrationCertNo(), request.getRegistrationCertNo(), entity::setRegistrationCertNo, changes);
        applyIfChanged("detectionMethod", entity.getDetectionMethod(), request.getDetectionMethod(), entity::setDetectionMethod, changes);
        applyIfChanged("manufacturer", entity.getManufacturer(), request.getManufacturer(), entity::setManufacturer, changes);
        applyIfChanged("distributor", entity.getDistributor(), request.getDistributor(), entity::setDistributor, changes);
        applyIfChanged("specification", entity.getSpecification(), request.getSpecification(), entity::setSpecification, changes);
        applyIfChanged("unit", entity.getUnit(), request.getUnit(), entity::setUnit, changes);
        applyIfChanged("storageCondition", entity.getStorageCondition(), request.getStorageCondition(), entity::setStorageCondition, changes);
        applyIfChanged("testGroups", entity.getTestGroups(), request.getTestGroups(), entity::setTestGroups, changes);
        applyIfChanged("testsPerKit", entity.getTestsPerKit(), request.getTestsPerKit(), entity::setTestsPerKit, changes);
        applyIfChanged("pricePerUnit", entity.getPricePerUnit(), request.getPricePerUnit(), entity::setPricePerUnit, changes);
        applyIfChanged("stabilityDaysAfterOpen", entity.getStabilityDaysAfterOpen(), request.getStabilityDaysAfterOpen(), entity::setStabilityDaysAfterOpen, changes);
        applyIfChanged("leadTimeDays", entity.getLeadTimeDays(), request.getLeadTimeDays(), entity::setLeadTimeDays, changes);

        if (request.getMaterialType() != null && request.getMaterialType() != entity.getMaterialType()) {
            changes.put("materialType", Map.of("old", entity.getMaterialType().getCode(),
                    "new", request.getMaterialType().getCode()));
            entity.setMaterialType(request.getMaterialType());
        }
        if (request.getDeviceBindMode() != null && request.getDeviceBindMode() != entity.getDeviceBindMode()) {
            changes.put("deviceBindMode", Map.of("old", entity.getDeviceBindMode().getCode(),
                    "new", request.getDeviceBindMode().getCode()));
            entity.setDeviceBindMode(request.getDeviceBindMode());
        }
        if (request.getEnableUniqueCode() != null && !request.getEnableUniqueCode().equals(entity.getEnableUniqueCode())) {
            changes.put("enableUniqueCode", Map.of("old", entity.getEnableUniqueCode(),
                    "new", request.getEnableUniqueCode()));
            entity.setEnableUniqueCode(request.getEnableUniqueCode());
        }

        applyIfChanged("isVolumeBased", entity.getIsVolumeBased(), request.getIsVolumeBased(), entity::setIsVolumeBased, changes);
        applyIfChanged("volumeType", entity.getVolumeType(), request.getVolumeType(), entity::setVolumeType, changes);
        applyIfChanged("isInsurancePayment", entity.getIsInsurancePayment(), request.getIsInsurancePayment(), entity::setIsInsurancePayment, changes);
        applyIfChanged("insurancePaymentType", entity.getInsurancePaymentType(), request.getInsurancePaymentType(), entity::setInsurancePaymentType, changes);
        applyIfChanged("isChargeable", entity.getIsChargeable(), request.getIsChargeable(), entity::setIsChargeable, changes);
        applyIfChanged("chargeCode", entity.getChargeCode(), request.getChargeCode(), entity::setChargeCode, changes);

        if (changes.isEmpty()) {
            log.info("试剂编辑无变更: id={}", id);
            return toVO(entity);
        }

        entity.setUpdatedAt(LocalDateTime.now());
        catalogMapper.updateById(entity);
        log.info("试剂已编辑: id={}, changes={}", id, changes.keySet());

        // 条件更新五码映射
        if (entity.getMaterialType().requiresFiveCodeMapping()) {
            fiveCodeMappingService.updateMapping(
                    entity.getId(),
                    request.getUdiDi(),
                    request.getGtin(),
                    request.getInsuranceCode(),
                    request.getRegistrationCertNo()
            );
        }

        return toVO(entity);
    }

    // ──────────────────────────────────────────────
    //  删除
    // ──────────────────────────────────────────────

    @Override
    public void delete(Long id) {
        ReagentCatalog entity = catalogMapper.selectById(id);
        if (entity == null) {
            throw new BizException(40007, "试剂不存在: id=" + id);
        }
        // MyBatis-Plus @TableLogic 自动执行软删除
        catalogMapper.deleteById(id);
        // 硬删除五码映射（该表无 is_deleted）
        fiveCodeMappingService.deleteByReagentId(id);
        log.info("试剂已软删除: id={}, hisCode={}", id, entity.getHisCode());
    }

    // ──────────────────────────────────────────────
    //  审批 — 提交
    // ──────────────────────────────────────────────

    @Override
    public void submitApproval(Long id, SubmitApprovalRequest request) {
        ReagentCatalog entity = catalogMapper.selectById(id);
        if (entity == null) {
            throw new BizException(40007, "试剂不存在: id=" + id);
        }

        // 防止重复提交
        if (approvalLogService.hasPending(id)) {
            throw new ApprovalPendingException(id);
        }

        // 按现有实体快照构建 field_changes JSON
        String changeType = (entity.getApprovalStatus() == ApprovalStatusEnum.DRAFT
                && entity.getCreatedAt().equals(entity.getUpdatedAt())) ? "CREATE" : "UPDATE";

        String fieldChangesJson;
        try {
            fieldChangesJson = OBJECT_MAPPER.writeValueAsString(entity);
        } catch (JsonProcessingException e) {
            log.error("序列化试剂实体失败: id={}", id, e);
            fieldChangesJson = "{}";
        }

        // 创建审批日志
        approvalLogService.createLog(
                id, changeType, fieldChangesJson,
                request.getChangeReason(),
                request.getSubmitterId(), request.getSubmitterName()
        );

        // 试剂状态 → PENDING
        entity.setApprovalStatus(ApprovalStatusEnum.PENDING);
        entity.setUpdatedAt(LocalDateTime.now());
        catalogMapper.updateById(entity);

        log.info("审批已提交: reagentId={}, changeType={}, submitterId={}",
                id, changeType, request.getSubmitterId());
    }

    // ──────────────────────────────────────────────
    //  审批 — 通过
    // ──────────────────────────────────────────────

    @Override
    public void approve(Long approvalLogId, ReviewRequest request) {
        CatalogApprovalLog logEntry = approvalLogService.getById(approvalLogId);
        if (logEntry == null) {
            throw new BizException(40400, "审批记录不存在: id=" + approvalLogId);
        }
        if (!"PENDING".equals(logEntry.getReviewStatus())) {
            throw new BizException(40005, "该审批记录已处理，当前状态: " + logEntry.getReviewStatus());
        }

        // 更新审批日志
        approvalLogService.approve(approvalLogId, request.getReviewerId(),
                request.getReviewerName(), request.getComment());

        // 更新试剂状态 → APPROVED
        ReagentCatalog entity = catalogMapper.selectById(logEntry.getReagentId());
        if (entity != null) {
            entity.setApprovalStatus(ApprovalStatusEnum.APPROVED);
            entity.setUpdatedAt(LocalDateTime.now());
            catalogMapper.updateById(entity);
        }

        log.info("审批已通过: approvalLogId={}, reagentId={}", approvalLogId, logEntry.getReagentId());
    }

    // ──────────────────────────────────────────────
    //  审批 — 驳回
    // ──────────────────────────────────────────────

    @Override
    public void reject(Long approvalLogId, ReviewRequest request) {
        CatalogApprovalLog logEntry = approvalLogService.getById(approvalLogId);
        if (logEntry == null) {
            throw new BizException(40400, "审批记录不存在: id=" + approvalLogId);
        }
        if (!"PENDING".equals(logEntry.getReviewStatus())) {
            throw new BizException(40005, "该审批记录已处理，当前状态: " + logEntry.getReviewStatus());
        }
        if (isBlank(request.getComment())) {
            throw new BizException(40006, "驳回时必须填写审批意见");
        }

        // 更新审批日志
        approvalLogService.reject(approvalLogId, request.getReviewerId(),
                request.getReviewerName(), request.getComment());

        // 更新试剂状态 → REJECTED
        ReagentCatalog entity = catalogMapper.selectById(logEntry.getReagentId());
        if (entity != null) {
            entity.setApprovalStatus(ApprovalStatusEnum.REJECTED);
            entity.setUpdatedAt(LocalDateTime.now());
            catalogMapper.updateById(entity);
        }

        log.info("审批已驳回: approvalLogId={}, reagentId={}", approvalLogId, logEntry.getReagentId());
    }

    // ──────────────────────────────────────────────
    //  审批列表 & 历史
    // ──────────────────────────────────────────────

    @Override
    public PageVO<CatalogApprovalLog> pagePendingApprovals(int page, int pageSize) {
        IPage<CatalogApprovalLog> iPage = approvalLogService.pagePending(page, pageSize);
        return PageVO.of(iPage);
    }

    @Override
    public PageVO<CatalogApprovalLog> pageApprovalHistory(int page, int pageSize) {
        IPage<CatalogApprovalLog> iPage = approvalLogService.pageHistory(page, pageSize);
        return PageVO.of(iPage);
    }

    // ──────────────────────────────────────────────
    //  UDI 待补全
    // ──────────────────────────────────────────────

    @Override
    public PageVO<UdiBacklog> pageUdiBacklog(int page, int pageSize, String impactLevel) {
        IPage<UdiBacklog> iPage = udiBacklogService.pageList(page, pageSize, impactLevel);
        return PageVO.of(iPage);
    }

    // ──────────────────────────────────────────────
    //  工具方法
    // ──────────────────────────────────────────────

    /**
     * Entity → VO 转换（含枚举 label + 嵌套五码映射）
     */
    private ReagentCatalogVO toVO(ReagentCatalog entity) {
        ReagentCatalogVO vo = new ReagentCatalogVO();
        vo.setId(entity.getId());
        vo.setHisCode(entity.getHisCode());
        vo.setGenericName(entity.getGenericName());

        if (entity.getMaterialType() != null) {
            vo.setMaterialType(entity.getMaterialType().getCode());
            vo.setMaterialTypeLabel(entity.getMaterialType().getLabel());
        }
        vo.setManufacturer(entity.getManufacturer());
        vo.setDistributor(entity.getDistributor());
        vo.setSpecification(entity.getSpecification());
        vo.setUnit(entity.getUnit());
        vo.setStorageCondition(entity.getStorageCondition());
        vo.setTestGroups(entity.getTestGroups());
        vo.setUdiDi(entity.getUdiDi());
        vo.setGtin(entity.getGtin());
        vo.setInsuranceCode(entity.getInsuranceCode());
        vo.setRegistrationCertNo(entity.getRegistrationCertNo());
        vo.setDetectionMethod(entity.getDetectionMethod());
        vo.setTestsPerKit(entity.getTestsPerKit());
        vo.setPricePerUnit(entity.getPricePerUnit());
        vo.setStabilityDaysAfterOpen(entity.getStabilityDaysAfterOpen());
        vo.setLeadTimeDays(entity.getLeadTimeDays());
        vo.setEnableUniqueCode(entity.getEnableUniqueCode());
        vo.setIsVolumeBased(entity.getIsVolumeBased());
        vo.setVolumeType(entity.getVolumeType());
        vo.setIsInsurancePayment(entity.getIsInsurancePayment());
        vo.setInsurancePaymentType(entity.getInsurancePaymentType());
        vo.setIsChargeable(entity.getIsChargeable());
        vo.setChargeCode(entity.getChargeCode());

        if (entity.getDeviceBindMode() != null) {
            vo.setDeviceBindMode(entity.getDeviceBindMode().getCode());
            vo.setDeviceBindModeLabel(entity.getDeviceBindMode().getLabel());
        }
        if (entity.getApprovalStatus() != null) {
            vo.setApprovalStatus(entity.getApprovalStatus().getCode());
            vo.setApprovalStatusLabel(entity.getApprovalStatus().getLabel());
        }
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());

        // 嵌套五码映射
        FiveCodeMapping mapping = fiveCodeMappingService.getByReagentId(entity.getId());
        if (mapping != null) {
            vo.setFiveCodeMapping(toFiveCodeMappingVO(mapping));
        }

        return vo;
    }

    private FiveCodeMappingVO toFiveCodeMappingVO(FiveCodeMapping mapping) {
        FiveCodeMappingVO vo = new FiveCodeMappingVO();
        vo.setId(mapping.getId());
        vo.setReagentId(mapping.getReagentId());
        vo.setUdiDi(mapping.getUdiDi());
        vo.setGtin(mapping.getGtin());
        vo.setInsuranceCode(mapping.getInsuranceCode());
        vo.setNmpaRegNo(mapping.getNmpaRegNo());
        vo.setHisCode(mapping.getHisCode());
        if (mapping.getMappingStatus() != null) {
            vo.setMappingStatus(mapping.getMappingStatus().getCode());
            vo.setMappingStatusLabel(mapping.getMappingStatus().getLabel());
        }
        return vo;
    }

    /**
     * 比较并应用字段变更，记录到 changes map（类型安全，编译期校验）
     */
    private static <T> void applyIfChanged(String fieldName, T oldVal, T newVal,
                                           java.util.function.Consumer<T> setter,
                                           Map<String, Object> changes) {
        if (newVal == null) return;
        if (newVal.equals(oldVal)) return;
        changes.put(fieldName, Map.of("old", oldVal != null ? oldVal : "null", "new", newVal));
        setter.accept(newVal);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
