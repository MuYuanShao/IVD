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
    private final ObjectMapper objectMapper;
    private final com.zlth.ivd.catalog.mapper.CatalogDraftMapper draftMapper;
    private final com.zlth.ivd.catalog.mapper.CatalogChangeLogMapper changeMapper;

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
        var vo = toVO(workingCopy(entity));
        vo.setEditingDraft(draftMapper.selectById(id) != null);
        return vo;
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
        validateCatalog(entity);

        // 4. 持久化
        catalogMapper.insert(entity);
        appendChange(entity, "CREATE", Map.of("created", Map.of("new", entity.getGenericName())));
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
        ReagentCatalog entity = lockCatalog(id);
        if (entity == null) {
            throw new BizException(40007, "试剂不存在: id=" + id);
        }
        if (approvalLogService.hasPending(id)) throw new BizException(40900, "待审批内容已冻结，请先完成或驳回审批");
        entity = workingCopy(entity);

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
        ReagentCatalog edited = entity;
        clearIfRequested("stabilityDaysAfterOpen", request.isStabilityDaysPresent(), request.getStabilityDaysAfterOpen(),
                entity.getStabilityDaysAfterOpen(), () -> edited.setStabilityDaysAfterOpen(null), changes);
        clearIfRequested("pricePerUnit", request.isPricePresent(), request.getPricePerUnit(),
                entity.getPricePerUnit(), () -> edited.setPricePerUnit(null), changes);
        clearIfRequested("testsPerKit", request.isTestsPresent(), request.getTestsPerKit(),
                entity.getTestsPerKit(), () -> edited.setTestsPerKit(null), changes);
        if (request.isLeadTimePresent() && request.getLeadTimeDays() == null)
            applyIfChanged("leadTimeDays", entity.getLeadTimeDays(), 7, entity::setLeadTimeDays, changes);
        validateCatalog(entity);

        if (changes.isEmpty()) {
            log.info("试剂编辑无变更: id={}", id);
            var unchanged = toVO(entity);
            unchanged.setEditingDraft(draftMapper.selectById(id) != null);
            return unchanged;
        }

        entity.setUpdatedAt(LocalDateTime.now());
        entity.setApprovalStatus(ApprovalStatusEnum.DRAFT);
        saveDraft(entity);
        catalogMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ReagentCatalog>()
                .eq(ReagentCatalog::getId, id).set(ReagentCatalog::getApprovalStatus, ApprovalStatusEnum.DRAFT)
                .set(ReagentCatalog::getUpdatedAt, entity.getUpdatedAt()));
        appendChange(entity, "UPDATE", changes);
        log.info("试剂已编辑: id={}, changes={}", id, changes.keySet());

        // 条件更新五码映射
        // Mapping and effective catalog fields are applied only when this draft is approved.

        var vo = toVO(entity);
        vo.setEditingDraft(true);
        return vo;
    }

    // ──────────────────────────────────────────────
    //  删除
    // ──────────────────────────────────────────────

    @Override
    public void delete(Long id) {
        ReagentCatalog entity = lockCatalog(id);
        if (entity == null) {
            throw new BizException(40007, "试剂不存在: id=" + id);
        }
        if (approvalLogService.hasPending(id)) throw new BizException(40900, "待审批试剂不能删除");
        appendChange(entity, "DELETE", Map.of("isDeleted", Map.of("old", 0, "new", 1)));
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
        ReagentCatalog entity = lockCatalog(id);
        if (entity == null) {
            throw new BizException(40007, "试剂不存在: id=" + id);
        }

        // 防止重复提交
        if (approvalLogService.hasPending(id)) {
            throw new ApprovalPendingException(id);
        }
        entity = workingCopy(entity);
        validateCatalog(entity);

        // 按现有实体快照构建 field_changes JSON
        String changeType = (entity.getApprovalStatus() == ApprovalStatusEnum.DRAFT
                && draftMapper.selectById(id) == null) ? "CREATE" : "UPDATE";

        String fieldChangesJson;
        try {
            fieldChangesJson = objectMapper.writeValueAsString(entity);
        } catch (JsonProcessingException e) {
            throw new BizException(50000, "审批快照保存失败，未提交审批");
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
        catalogMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ReagentCatalog>()
                .eq(ReagentCatalog::getId, id).set(ReagentCatalog::getApprovalStatus, ApprovalStatusEnum.PENDING)
                .set(ReagentCatalog::getUpdatedAt, entity.getUpdatedAt()));

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
        ReagentCatalog locked = lockCatalog(logEntry.getReagentId());
        logEntry = approvalLogService.lockById(approvalLogId);
        if (!"PENDING".equals(logEntry.getReviewStatus())) {
            throw new BizException(40005, "该审批记录已处理，当前状态: " + logEntry.getReviewStatus());
        }
        if (locked == null) throw new BizException(40400, "试剂不存在");
        com.zlth.ivd.catalog.entity.CatalogDraft draft = draftMapper.selectById(locked.getId());
        ReagentCatalog submitted = readSnapshot(logEntry.getFieldChanges());
        ReagentCatalog expected = draft == null ? locked : readSnapshot(draft.getSnapshot());
        if (!java.util.Objects.equals(submitted.getId(), locked.getId()) || !sameEditableFields(submitted, expected))
            throw new BizException(40900, "提交快照与当前待审批版本不一致，请驳回后重新提交");
        if (draft != null) {
            ReagentCatalog candidate = readSnapshot(draft.getSnapshot());
            validateCatalog(candidate);
            applyApprovedFields(candidate);
            if (candidate.getMaterialType().requiresFiveCodeMapping()) {
                if (fiveCodeMappingService.getByReagentId(candidate.getId()) == null) {
                    fiveCodeMappingService.createMapping(candidate.getId(), candidate.getHisCode(), candidate.getUdiDi(),
                            candidate.getGtin(), candidate.getInsuranceCode(), candidate.getRegistrationCertNo());
                } else fiveCodeMappingService.updateMapping(candidate.getId(), candidate.getUdiDi(), candidate.getGtin(),
                        candidate.getInsuranceCode(), candidate.getRegistrationCertNo());
            }
            draftMapper.deleteById(locked.getId());
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
        lockCatalog(logEntry.getReagentId());
        logEntry = approvalLogService.lockById(approvalLogId);
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
        if (newVal instanceof BigDecimal next && oldVal instanceof BigDecimal previous && next.compareTo(previous) == 0) return;
        if (newVal.equals(oldVal)) return;
        Map<String,Object> difference = new LinkedHashMap<>();
        difference.put("old", oldVal); difference.put("new", newVal);
        changes.put(fieldName, difference);
        setter.accept(newVal);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private void clearIfRequested(String field, boolean present, Object requested, Object existing,
                                  Runnable clear, Map<String,Object> changes) {
        if (!present || requested != null || existing == null) return;
        var difference = new LinkedHashMap<String,Object>();
        difference.put("old", existing); difference.put("new", null);
        changes.put(field, difference); clear.run();
    }

    private ReagentCatalog lockCatalog(Long id) {
        return catalogMapper.selectOne(new LambdaQueryWrapper<ReagentCatalog>()
                .eq(ReagentCatalog::getId, id).last("FOR UPDATE"));
    }

    private ReagentCatalog workingCopy(ReagentCatalog entity) {
        var draft = draftMapper.selectById(entity.getId());
        if (draft == null) return entity;
        ReagentCatalog copy = readSnapshot(draft.getSnapshot());
        copy.setApprovalStatus(entity.getApprovalStatus());
        return copy;
    }

    private ReagentCatalog readSnapshot(String json) {
        try {
            var node = objectMapper.readTree(json);
            if (!(node instanceof com.fasterxml.jackson.databind.node.ObjectNode object))
                throw new BizException(40900, "审批快照格式无效");
            for (String field : new String[]{"createdAt", "updatedAt"}) {
                var value = object.get(field);
                if (value != null && value.isTextual()) object.put(field, value.asText().replace(' ', 'T'));
            }
            return objectMapper.treeToValue(object, ReagentCatalog.class);
        }
        catch (JsonProcessingException e) { throw new BizException(50000, "目录草稿损坏，操作已拒绝"); }
    }

    private void saveDraft(ReagentCatalog entity) {
        var draft = new com.zlth.ivd.catalog.entity.CatalogDraft();
        draft.setReagentId(entity.getId());
        try { draft.setSnapshot(objectMapper.writeValueAsString(entity)); }
        catch (JsonProcessingException e) { throw new BizException(50000, "目录草稿保存失败"); }
        draft.setUpdatedAt(LocalDateTime.now());
        if (draftMapper.selectById(entity.getId()) == null) draftMapper.insert(draft);
        else draftMapper.updateById(draft);
    }

    private void appendChange(ReagentCatalog entity, String type, Map<String,Object> changes) {
        var entry = new com.zlth.ivd.catalog.entity.CatalogChangeLog();
        entry.setReagentId(entity.getId()); entry.setReagentName(entity.getGenericName()); entry.setHisCode(entity.getHisCode());
        entry.setChangeType(type);
        try { entry.setFieldChanges(objectMapper.writeValueAsString(changes)); }
        catch (JsonProcessingException e) { throw new BizException(50000, "目录变更记录保存失败"); }
        entry.setOperatorId(com.zlth.ivd.common.utils.SecurityUtils.getCurrentUserIdOrNull());
        entry.setOperatorName(com.zlth.ivd.common.utils.SecurityUtils.getCurrentUsername());
        entry.setCreatedAt(LocalDateTime.now());
        changeMapper.insert(entry);
    }

    private void validateCatalog(ReagentCatalog entity) {
        if (isBlank(entity.getGenericName()) || isBlank(entity.getManufacturer()) || isBlank(entity.getDistributor())
                || entity.getMaterialType() == null) throw new BizException(40001, "名称、厂家、配送商及物料类型必填");
        if (entity.getMaterialType().isRegistrationCertRequired() && isBlank(entity.getRegistrationCertNo()))
            throw new BizException(40003, "该物料类型必须填写注册证号");
        if (entity.getMaterialType().isTestsPerKitRequired() && entity.getTestsPerKit() == null)
            throw new BizException(40004, "试剂必须填写标称测试数");
        if (entity.getTestsPerKit() != null && entity.getTestsPerKit() <= 0
                || entity.getPricePerUnit() != null && entity.getPricePerUnit().signum() < 0
                || entity.getStabilityDaysAfterOpen() != null && entity.getStabilityDaysAfterOpen() < 0
                || entity.getLeadTimeDays() == null || entity.getLeadTimeDays() < 0)
            throw new BizException(40001, "测试数须大于零，单价及天数不得为负");
        for (Integer flag : new Integer[]{entity.getEnableUniqueCode(), entity.getIsVolumeBased(),
                entity.getIsInsurancePayment(), entity.getIsChargeable()})
            if (flag != null && flag != 0 && flag != 1) throw new BizException(40001, "开关字段仅允许0或1");
    }

    private void applyApprovedFields(ReagentCatalog entity) {
        var update = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ReagentCatalog>().eq("id", entity.getId());
        // Exact draft application including explicit nulls; DTO fields define editable properties.
        var bean = new org.springframework.beans.BeanWrapperImpl(entity);
        for (var field : UpdateReagentRequest.class.getDeclaredFields()) {
            if (!bean.isReadableProperty(field.getName())) continue;
            String column = field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
            update.set(column, bean.getPropertyValue(field.getName()));
        }
        catalogMapper.update(null, update);
    }

    private boolean sameEditableFields(ReagentCatalog left, ReagentCatalog right) {
        var a = new org.springframework.beans.BeanWrapperImpl(left);
        var b = new org.springframework.beans.BeanWrapperImpl(right);
        for (var field : UpdateReagentRequest.class.getDeclaredFields()) {
            if (!a.isReadableProperty(field.getName())) continue;
            Object x = a.getPropertyValue(field.getName()), y = b.getPropertyValue(field.getName());
            if (x instanceof BigDecimal dx && y instanceof BigDecimal dy) {
                if (dx.compareTo(dy) != 0) return false;
            } else if (!java.util.Objects.equals(x, y)) return false;
        }
        return true;
    }

    @Override
    public PageVO<com.zlth.ivd.common.vo.ChangeLogVO> pageChangeLogs(com.zlth.ivd.common.dto.ChangeLogQuery query) {
        if (query.getPage() == null || query.getPage() < 1 || query.getPageSize() == null
                || query.getPageSize() < 1 || query.getPageSize() > 10000)
            throw new BizException(40001, "目录变更记录分页参数非法");
        var requestPage = new Page<com.zlth.ivd.common.vo.ChangeLogVO>(query.getPage(),query.getPageSize());
        requestPage.setMaxLimit((long)query.getPageSize());
        var page = changeMapper.pageChanges(requestPage, query);
        page.getRecords().forEach(vo -> { vo.setModule("CATALOG"); vo.setModuleLabel("试剂目录");
            vo.setChangeTypeLabel(switch (vo.getChangeType()) { case "CREATE" -> "新增试剂"; case "UPDATE" -> "编辑试剂"; case "DELETE" -> "删除试剂"; default -> vo.getChangeType(); }); });
        return PageVO.of(page);
    }
}
