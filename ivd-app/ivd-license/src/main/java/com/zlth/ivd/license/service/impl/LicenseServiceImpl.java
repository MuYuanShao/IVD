package com.zlth.ivd.license.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlth.ivd.common.dto.ChangeLogQuery;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.vo.ChangeLogVO;
import com.zlth.ivd.common.enums.LicenseStatusEnum;
import com.zlth.ivd.common.enums.LicenseTypeEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.config.MinioProperties;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.license.dto.CreateLicenseRequest;
import com.zlth.ivd.license.dto.LicenseQuery;
import com.zlth.ivd.license.dto.UpdateLicenseRequest;
import com.zlth.ivd.license.entity.LicenseChangeLog;
import com.zlth.ivd.license.entity.SupplierLicense;
import com.zlth.ivd.license.mapper.LicenseChangeLogMapper;
import com.zlth.ivd.license.mapper.ReagentSupplierRelMapper;
import com.zlth.ivd.license.mapper.SupplierLicenseMapper;
import com.zlth.ivd.license.mapper.SupplierMapper;
import com.zlth.ivd.license.service.LicenseService;
import com.zlth.ivd.license.service.LicenseExpiryPolicy;
import com.zlth.ivd.license.vo.LicenseDetailVO;
import com.zlth.ivd.license.vo.LicenseVO;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 证照管理服务实现
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class LicenseServiceImpl implements LicenseService {

    private final SupplierLicenseMapper licenseMapper;
    private final SupplierMapper supplierMapper;
    private final LicenseChangeLogMapper changeLogMapper;
    private final ReagentSupplierRelMapper reagentSupplierRelMapper;
    private final com.zlth.ivd.catalog.service.ReagentCatalogService catalogService;
    private final MinioClient minioClient;
    private final MinioProperties minioProps;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public PageVO<LicenseVO> listLicenses(LicenseQuery query) {
        LambdaQueryWrapper<SupplierLicense> qw = new LambdaQueryWrapper<>();
        if (query.getReagentId() != null) {
            Set<Long> supplierIds = reagentSupplierRelMapper.selectList(
                    new LambdaQueryWrapper<com.zlth.ivd.license.entity.ReagentSupplierRel>()
                            .eq(com.zlth.ivd.license.entity.ReagentSupplierRel::getReagentId, query.getReagentId()))
                    .stream()
                    .map(com.zlth.ivd.license.entity.ReagentSupplierRel::getSupplierId)
                    .collect(Collectors.toSet());
            if (supplierIds.isEmpty()) {
                return new PageVO<>(0L, query.getPage(), query.getPageSize(), Collections.emptyList());
            }
            qw.in(SupplierLicense::getSupplierId, supplierIds);
        }
        if (StringUtils.hasText(query.getLicenseType())) {
            qw.eq(SupplierLicense::getLicenseType, LicenseTypeEnum.valueOf(query.getLicenseType()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            applyEffectiveStatusFilter(qw, query.getStatus());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String pattern = "%" + query.getKeyword().strip()
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            qw.and(search -> search.apply("cert_no LIKE {0} ESCAPE '!'", pattern)
                    .or().apply("""
                            EXISTS (SELECT 1 FROM supplier s
                            WHERE s.id = supplier_license.supplier_id
                              AND s.institution_id = supplier_license.institution_id
                              AND s.name LIKE {0} ESCAPE '!')
                            """, pattern));
        }
        qw.orderByDesc(SupplierLicense::getCreatedAt, SupplierLicense::getId);

        Page<SupplierLicense> page = licenseMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()), qw);

        List<LicenseVO> records = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return new PageVO<>(page.getTotal(), query.getPage(), query.getPageSize(), records);
    }

    @Override
    public LicenseDetailVO getDetail(Long id) {
        SupplierLicense license = licenseMapper.selectById(id);
        if (license == null) {
            throw new BizException(40400, "证照不存在");
        }

        var supplier = supplierMapper.selectById(license.getSupplierId());

        // 变更记录
        List<LicenseChangeLog> logs = changeLogMapper.findByLicenseId(id);
        List<LicenseDetailVO.ChangeLogItem> changeItems = logs.stream()
                .map(l -> LicenseDetailVO.ChangeLogItem.builder()
                        .id(l.getId())
                        .changeType(l.getChangeType())
                        .changeTypeLabel(changeTypeLabel(l.getChangeType()))
                        .oldExpireDate(l.getOldExpireDate())
                        .newExpireDate(l.getNewExpireDate())
                        .operatorName(l.getOperatorName())
                        .note(l.getNote())
                        .createdAt(l.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        var proofReferences = renewalProofs(logs);
        var attachments = parseAttachments(license.getAttachments());
        attachments.forEach(attachment -> attachment.setProtectedEvidence(proofReferences.uncertain()
                || proofReferences.keys().contains(attachment.getObjectKey())));
        var today = LocalDate.now();
        var effectiveStatus = LicenseExpiryPolicy.status(license.getStatus(), license.getExpireDate(), today);
        String reminder = LicenseExpiryPolicy.reminderLevel(license.getStatus(), license.getExpireDate(), today);

        return LicenseDetailVO.builder()
                .id(license.getId())
                .supplierId(license.getSupplierId())
                .supplierName(supplier != null ? supplier.getName() : "")
                .licenseType(license.getLicenseType().name())
                .licenseTypeLabel(license.getLicenseType().getLabel())
                .certNo(license.getCertNo())
                .issuingAuthority(license.getIssuingAuthority())
                .issueDate(license.getIssueDate())
                .expireDate(license.getExpireDate())
                .status(effectiveStatus.name())
                .statusLabel(effectiveStatus.getLabel())
                .remainingDays(LicenseExpiryPolicy.remainingDays(license.getExpireDate(), today))
                .reminderLevel(reminder)
                .reminderLabel(LicenseExpiryPolicy.reminderLabel(reminder))
                .relatedReagents(relatedReagents(license.getSupplierId()))
                .attachments(attachments)
                .changeLogs(changeItems)
                .createdAt(license.getCreatedAt())
                .updatedAt(license.getUpdatedAt())
                .build();
    }

    @Override
    public LicenseVO create(CreateLicenseRequest request) {
        Long operatorId = com.zlth.ivd.common.security.InstitutionContext.requireUserActor();
        validateLicenseDates(request.getIssueDate(), request.getExpireDate());
        if (request.getSupplierId() == null || request.getSupplierId() <= 0)
            throw new BizException(40001, "请选择当前机构的供应商");
        if (supplierMapper.selectById(request.getSupplierId()) == null)
            throw new BizException(40400, "供应商不存在或不属于当前机构");
        SupplierLicense license = new SupplierLicense();
        license.setSupplierId(request.getSupplierId());
        license.setLicenseType(LicenseTypeEnum.valueOf(request.getLicenseType()));
        license.setCertNo(request.getCertNo());
        license.setIssuingAuthority(request.getIssuingAuthority());
        license.setIssueDate(request.getIssueDate());
        license.setExpireDate(request.getExpireDate());
        license.setStatus(calcStatus(request.getExpireDate()));

        licenseMapper.insert(license);

        // 记录变更日志
        insertChangeLog(license.getId(), "CREATE", null, request.getExpireDate(),
                operatorId, SecurityUtils.getCurrentUsername(), "创建证照");

        return toVO(license);
    }

    @Override
    public LicenseVO approveRenewal(Long id, com.zlth.ivd.license.dto.ApproveLicenseRenewalRequest request) {
        if (!Set.of("SYSTEM_ADMIN", "LAB_DIRECTOR").contains(SecurityUtils.getCurrentRole()))
            throw new BizException(40300, "仅主任或系统管理员可审批续证");
        Long actor = com.zlth.ivd.common.security.InstitutionContext.requireUserActor();
        SupplierLicense license = licenseMapper.selectOne(new LambdaQueryWrapper<SupplierLicense>()
                .eq(SupplierLicense::getId, id).last("FOR UPDATE"));
        if (license == null) throw new BizException(40400, "证照不存在");
        validateLicenseDates(request.getIssueDate(), request.getExpireDate());
        if (request.getIssueDate().isAfter(LocalDate.now()) || !request.getExpireDate().isAfter(LocalDate.now())
                || !StringUtils.hasText(request.getCertNo()) || !StringUtils.hasText(request.getReason()))
            throw new BizException(40001, "续证证号、有效日期及审批原因不完整或无效");
        if (parseAttachments(license.getAttachments()).stream()
                .noneMatch(attachment -> Objects.equals(attachment.getObjectKey(), request.getAttachmentKey())))
            throw new BizException(40001, "必须先上传并选择本证照的续证证明附件");
        try {
            minioClient.statObject(io.minio.StatObjectArgs.builder().bucket(minioProps.getBucketCert())
                    .object("license/" + id + "/" + request.getAttachmentKey()).build());
        } catch (Exception unavailable) {
            throw new BizException(40001, "续证附件不可访问，请重新上传并核验");
        }
        if ((license.getStatus() == LicenseStatusEnum.ACTIVE || license.getStatus() == LicenseStatusEnum.EXPIRING)
                && Objects.equals(license.getCertNo(), request.getCertNo().strip())
                && Objects.equals(license.getIssueDate(), request.getIssueDate())
                && Objects.equals(license.getExpireDate(), request.getExpireDate())) return toVO(license);
        LocalDate oldExpiry = license.getExpireDate();
        String note;
        try {
            note = OBJECT_MAPPER.writeValueAsString(Map.of("reason", request.getReason(),
                    "attachmentKey", request.getAttachmentKey(), "oldCertNo", Objects.toString(license.getCertNo(), ""),
                    "newCertNo", request.getCertNo(), "oldStatus", Objects.toString(license.getStatus(), ""),
                    "oldIssueDate", Objects.toString(license.getIssueDate(), ""), "newIssueDate", request.getIssueDate().toString()));
        } catch (Exception error) { throw new BizException(50000, "续证审批证据保存失败"); }
        license.setCertNo(request.getCertNo().strip());
        license.setIssueDate(request.getIssueDate());
        license.setExpireDate(request.getExpireDate());
        license.setStatus(calcStatus(request.getExpireDate()));
        if (licenseMapper.updateById(license) != 1) throw new BizException(40900, "证照已变化，请重试");
        insertChangeLog(id, "RENEW", oldExpiry, request.getExpireDate(), actor, SecurityUtils.getCurrentUsername(), note);
        return toVO(license);
    }

    @Override
    public LicenseVO update(Long id, UpdateLicenseRequest request) {
        Long operatorId = com.zlth.ivd.common.security.InstitutionContext.requireUserActor();
        SupplierLicense license = licenseMapper.selectOne(new LambdaQueryWrapper<SupplierLicense>()
                .eq(SupplierLicense::getId, id).last("FOR UPDATE"));
        if (license == null) {
            throw new BizException(40400, "证照不存在");
        }

        LocalDate oldExpireDate = license.getExpireDate();
        validateLicenseDates(request.getIssueDate() != null ? request.getIssueDate() : license.getIssueDate(),
                request.getExpireDate() != null ? request.getExpireDate() : oldExpireDate);
        if (request.getSupplierId() != null && !request.getSupplierId().equals(license.getSupplierId())) {
            if (license.getStatus() == LicenseStatusEnum.FROZEN || license.getStatus() == LicenseStatusEnum.EXPIRED
                    || license.getExpireDate() == null || !license.getExpireDate().isAfter(LocalDate.now()))
                throw new BizException(40900, "失效或冻结证照不能通过更换供应商解除采购限制，请先完成续证审批");
            if (supplierMapper.selectById(request.getSupplierId()) == null)
                throw new BizException(40400, "供应商不存在或无权访问");
            license.setSupplierId(request.getSupplierId());
        }
        boolean frozen = license.getStatus() == LicenseStatusEnum.FROZEN
                || license.getStatus() == LicenseStatusEnum.EXPIRED
                || license.getExpireDate() == null || !license.getExpireDate().isAfter(LocalDate.now());
        if (frozen && license.getStatus() != LicenseStatusEnum.FROZEN) license.setStatus(LicenseStatusEnum.EXPIRED);
        LicenseStatusEnum requestedStatus = StringUtils.hasText(request.getStatus())
                ? LicenseStatusEnum.valueOf(request.getStatus()) : null;
        if (frozen && requestedStatus != null && requestedStatus != LicenseStatusEnum.FROZEN
                && requestedStatus != LicenseStatusEnum.EXPIRED)
            throw new BizException(40900, "普通编辑不能解除证照冻结");

        if (StringUtils.hasText(request.getLicenseType())) {
            license.setLicenseType(LicenseTypeEnum.valueOf(request.getLicenseType()));
        }
        if (StringUtils.hasText(request.getCertNo())) {
            license.setCertNo(request.getCertNo());
        }
        if (StringUtils.hasText(request.getIssuingAuthority())) {
            license.setIssuingAuthority(request.getIssuingAuthority());
        }
        if (request.getIssueDate() != null) {
            license.setIssueDate(request.getIssueDate());
        }
        if (request.getExpireDate() != null) {
            license.setExpireDate(request.getExpireDate());
            if (!frozen) license.setStatus(calcStatus(request.getExpireDate()));
        }
        if (requestedStatus != null) {
            license.setStatus(requestedStatus);
        }

        licenseMapper.updateById(license);

        // 记录变更日志
        insertChangeLog(license.getId(), !frozen && requestedStatus == LicenseStatusEnum.FROZEN ? "FREEZE" : "UPDATE", oldExpireDate,
                request.getExpireDate() != null ? request.getExpireDate() : oldExpireDate,
                operatorId, SecurityUtils.getCurrentUsername(),
                request.getChangeNote());

        return toVO(license);
    }

    @Override
    public Map<String, Long> getStats() {
        List<Map<String, Object>> rows = licenseMapper.countByStatus();
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", 0L);
        stats.put("active", 0L);
        stats.put("expiring", 0L);
        stats.put("expiredFrozen", 0L);
        stats.put("supplierCount", supplierMapper.selectCount(null));

        for (Map<String, Object> row : rows) {
            String status = (String) row.get("status");
            Long cnt = ((Number) row.get("cnt")).longValue();
            stats.put("total", stats.get("total") + cnt);
            if ("ACTIVE".equals(status)) stats.put("active", cnt);
            else if ("EXPIRING".equals(status)) stats.put("expiring", cnt);
            else stats.put("expiredFrozen", stats.get("expiredFrozen") + cnt);
        }
        return stats;
    }

    @Override
    public List<Map<String, Object>> getSuppliers() {
        return supplierMapper.selectList(null).stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("name", s.getName());
                    m.put("isActive", Boolean.TRUE.equals(s.getIsActive()));
                    return m;
                }).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<LicenseVO> getExpiryAlerts(int days) {
        if (days < 1 || days > 365) throw new BizException(40001, "证照预警天数须为1至365");
        return licenseMapper.findExpiringWithinDays(days).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public String uploadAttachment(Long licenseId, MultipartFile file, Long operatorId) {
        SupplierLicense license = licenseMapper.selectOne(new LambdaQueryWrapper<SupplierLicense>()
                .eq(SupplierLicense::getId, licenseId).last("FOR UPDATE"));
        if (license == null) {
            throw new BizException(40400, "证照不存在");
        }

        // 生成唯一对象名，并清理路径穿越字符
        String originalName = file.getOriginalFilename();
        String safeName = sanitizeFilename(originalName != null ? originalName : "file");
        String objectKey = java.util.UUID.randomUUID() + "_" + safeName;

        // MinIO 对象路径: license/{licenseId}/{objectKey}
        String objectName = "license/" + licenseId + "/" + objectKey;
        String contentType = guessContentType(originalName);
        List<LicenseDetailVO.AttachmentItem> attachments = parseAttachments(license.getAttachments());
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("证照附件上传需要事务");

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProps.getBucketCert())
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (ErrorResponseException e) {
            log.error("MinIO上传失败: licenseId={}, objectKey={}", licenseId, objectKey, e);
            throw new BizException(50000, "附件上传失败: MinIO服务异常");
        } catch (InsufficientDataException | IOException e) {
            log.error("附件数据异常: licenseId={}, fileName={}", licenseId, originalName, e);
            throw new BizException(50000, "附件上传失败: 文件数据异常");
        } catch (Exception e) {
            log.error("MinIO上传未知异常: licenseId={}, objectKey={}", licenseId, objectKey, e);
            throw new BizException(50000, "附件上传失败: 存储服务异常");
        }

        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_ROLLED_BACK) return;
                        try {
                            minioClient.removeObject(RemoveObjectArgs.builder()
                                    .bucket(minioProps.getBucketCert()).object(objectName).build());
                        } catch (Exception e) {
                            log.warn("证照上传回滚后清理对象失败: licenseId={}, objectKey={}", licenseId, objectKey, e);
                        }
                    }
                });

        // 构建附件条目（元数据仍存 DB JSON）
        String username = SecurityUtils.getCurrentUsername();
        LicenseDetailVO.AttachmentItem item = LicenseDetailVO.AttachmentItem.builder()
                .name(originalName != null ? originalName : "file")
                .objectKey(objectKey)
                .size(file.getSize())
                .type(getFileExtension(originalName))
                .uploadedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .uploadedBy(username != null ? username : "管理员")
                .build();

        // 更新 supplier_license.attachments JSON 字段
        attachments.add(item);

        String note;
        try {
            license.setAttachments(OBJECT_MAPPER.writeValueAsString(attachments));
            note = OBJECT_MAPPER.writeValueAsString(Map.of("fileName", item.getName(), "objectKey", objectKey));
        } catch (Exception e) {
            log.error("附件JSON序列化失败: licenseId={}", licenseId, e);
            throw new BizException(50000, "附件信息保存失败");
        }
        if (licenseMapper.updateById(license) != 1)
            throw new BizException(40900, "证照已变化，请刷新后重试");
        insertChangeLog(licenseId, "ADD_ATTACHMENT", null, null, operatorId, username, note);

        log.info("证照附件上传成功(MinIO): licenseId={}, bucket={}, objectName={}, fileName={}",
                licenseId, minioProps.getBucketCert(), objectName, originalName);
        return objectKey;
    }

    @Override
    public List<LicenseVO> exportAll() {
        return licenseMapper.selectList(
                new LambdaQueryWrapper<SupplierLicense>().orderByDesc(SupplierLicense::getCreatedAt)
        ).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public int importBatch(List<CreateLicenseRequest> requests) {
        int count = 0;
        for (CreateLicenseRequest req : requests) {
            try {
                create(req);
                count++;
            } catch (BizException e) {
                log.warn("导入证照跳过: {} — {}", req.getCertNo(), e.getMessage());
            }
        }
        return count;
    }

    @Override
    public void processExpiryCheck() {
        log.info("[XXL-Job] 证照效期检查开始");
        List<SupplierLicense> expiring = licenseMapper.selectList(new LambdaQueryWrapper<SupplierLicense>()
                .in(SupplierLicense::getStatus, LicenseStatusEnum.ACTIVE, LicenseStatusEnum.EXPIRING)
                .le(SupplierLicense::getExpireDate, LocalDate.now().plusDays(90)));
        for (SupplierLicense lic : expiring) {
            LicenseStatusEnum newStatus = calcStatus(lic.getExpireDate());
            if (newStatus != lic.getStatus()) {
                int updated = licenseMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SupplierLicense>()
                                .eq(SupplierLicense::getId, lic.getId())
                                .eq(SupplierLicense::getStatus, lic.getStatus())
                                .eq(SupplierLicense::getExpireDate, lic.getExpireDate())
                                .set(SupplierLicense::getStatus, newStatus)
                                .set(SupplierLicense::getUpdatedAt, LocalDateTime.now()));
                if (updated == 1) log.info("[XXL-Job] 证照 {} 状态更新: {} -> {}", lic.getId(), lic.getStatus(), newStatus);
            }
        }
        log.info("[XXL-Job] 证照效期检查完成，处理 {} 条", expiring.size());
    }

    @Override
    public void deleteAttachment(Long licenseId, String objectKey, Long operatorId) {
        SupplierLicense license = licenseMapper.selectOne(new LambdaQueryWrapper<SupplierLicense>()
                .eq(SupplierLicense::getId, licenseId).last("FOR UPDATE"));
        if (license == null) {
            throw new BizException(40400, "证照不存在");
        }

        List<LicenseDetailVO.AttachmentItem> attachments = parseAttachments(license.getAttachments());
        LicenseDetailVO.AttachmentItem target = attachments.stream()
                .filter(a -> objectKey.equals(a.getObjectKey()))
                .findFirst()
                .orElse(null);
        if (target == null) {
            throw new BizException(40400, "附件不存在");
        }
        requireMutableAttachment(licenseId, objectKey);

        cleanupAttachmentOnCompletion(licenseId, objectKey, true);

        // 2. 从 JSON 中移除
        attachments.remove(target);
        String note;
        try {
            license.setAttachments(OBJECT_MAPPER.writeValueAsString(attachments));
            note = OBJECT_MAPPER.writeValueAsString(Map.of("fileName", Objects.toString(target.getName(), ""), "objectKey", objectKey));
        } catch (Exception e) {
            log.error("附件JSON序列化失败: licenseId={}", licenseId, e);
            throw new BizException(50000, "附件信息保存失败");
        }
        if (licenseMapper.updateById(license) != 1) throw new BizException(40900, "证照已变化，请刷新后重试");

        // 3. 记录变更日志
        String operatorName = SecurityUtils.getCurrentUsername();
        insertChangeLog(licenseId, "DELETE_ATTACHMENT", null, null, operatorId, operatorName, note);

        log.info("证照附件已删除: licenseId={}, objectKey={}, fileName={}", licenseId, objectKey, target.getName());
    }

    @Override
    public String replaceAttachment(Long licenseId, String oldObjectKey, MultipartFile file, Long operatorId) {
        SupplierLicense license = licenseMapper.selectOne(new LambdaQueryWrapper<SupplierLicense>()
                .eq(SupplierLicense::getId, licenseId).last("FOR UPDATE"));
        if (license == null) {
            throw new BizException(40400, "证照不存在");
        }

        List<LicenseDetailVO.AttachmentItem> attachments = parseAttachments(license.getAttachments());
        LicenseDetailVO.AttachmentItem oldItem = attachments.stream()
                .filter(a -> oldObjectKey.equals(a.getObjectKey()))
                .findFirst()
                .orElse(null);
        if (oldItem == null) {
            throw new BizException(40400, "附件不存在");
        }
        requireMutableAttachment(licenseId, oldObjectKey);

        String oldFileName = oldItem.getName();

        // 1. 上传新文件到 MinIO
        String originalName = file.getOriginalFilename();
        String safeName = sanitizeFilename(originalName != null ? originalName : "file");
        String newObjectKey = java.util.UUID.randomUUID() + "_" + safeName;
        String newObjectName = "license/" + licenseId + "/" + newObjectKey;
        String contentType = guessContentType(originalName);
        cleanupAttachmentOnCompletion(licenseId, oldObjectKey, true);
        cleanupAttachmentOnCompletion(licenseId, newObjectKey, false);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProps.getBucketCert())
                    .object(newObjectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (ErrorResponseException e) {
            log.error("MinIO替换上传失败: licenseId={}, newObjectKey={}", licenseId, newObjectKey, e);
            throw new BizException(50000, "附件替换失败: MinIO服务异常");
        } catch (InsufficientDataException | IOException e) {
            log.error("附件数据异常: licenseId={}, fileName={}", licenseId, originalName, e);
            throw new BizException(50000, "附件替换失败: 文件数据异常");
        } catch (Exception e) {
            log.error("MinIO替换上传未知异常: licenseId={}, newObjectKey={}", licenseId, newObjectKey, e);
            throw new BizException(50000, "附件替换失败: 存储服务异常");
        }

        // 2. 更新元数据
        String username = SecurityUtils.getCurrentUsername();
        oldItem.setName(originalName != null ? originalName : "file");
        oldItem.setObjectKey(newObjectKey);
        oldItem.setSize(file.getSize());
        oldItem.setType(getFileExtension(originalName));
        oldItem.setUploadedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        oldItem.setUploadedBy(username != null ? username : "管理员");

        String note;
        try {
            license.setAttachments(OBJECT_MAPPER.writeValueAsString(attachments));
            note = OBJECT_MAPPER.writeValueAsString(Map.of("oldFileName", Objects.toString(oldFileName, ""),
                    "newFileName", oldItem.getName(), "oldKey", oldObjectKey, "newKey", newObjectKey));
        } catch (Exception e) {
            log.error("附件JSON序列化失败: licenseId={}", licenseId, e);
            throw new BizException(50000, "附件信息保存失败");
        }
        if (licenseMapper.updateById(license) != 1) throw new BizException(40900, "证照已变化，请刷新后重试");

        // 5. 记录变更日志
        String operatorName = SecurityUtils.getCurrentUsername();
        insertChangeLog(licenseId, "REPLACE_ATTACHMENT", null, null, operatorId, operatorName, note);

        log.info("证照附件已替换: licenseId={}, oldKey={} -> newKey={}, newFile={}",
                licenseId, oldObjectKey, newObjectKey, originalName);
        return newObjectKey;
    }

    private void cleanupAttachmentOnCompletion(Long licenseId, String key, boolean afterCommit) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != (afterCommit ? STATUS_COMMITTED : STATUS_ROLLED_BACK)) return;
                        try {
                            minioClient.removeObject(RemoveObjectArgs.builder().bucket(minioProps.getBucketCert())
                                    .object("license/" + licenseId + "/" + key).build());
                        } catch (Exception e) {
                            log.warn("证照附件事务后清理失败: licenseId={}, objectKey={}, status={}", licenseId, key, status, e);
                        }
                    }
                });
    }

    @Override
    public PageVO<ChangeLogVO> listChangeLogs(ChangeLogQuery query) {
        if (query.getPage() == null || query.getPage() < 1 || query.getPageSize() == null
                || query.getPageSize() < 1 || query.getPageSize() > 100)
            throw new BizException(40001, "变更记录分页参数非法");
        String module = query.getModule();
        boolean queryLicense = module == null || module.isEmpty() || "LICENSE".equalsIgnoreCase(module);
        boolean queryCatalog = module == null || module.isEmpty() || "CATALOG".equalsIgnoreCase(module);
        if (!queryLicense && !queryCatalog) throw new BizException(40001, "未知变更模块");
        boolean combined = queryLicense && queryCatalog;
        int requestedPage = query.getPage();
        int requestedSize = query.getPageSize();
        long prefix = (long) requestedPage * requestedSize;
        if (combined && prefix > 10000) throw new BizException(40001, "请筛选模块或日期后查询深页历史");
        int prefixSize = combined ? (int)prefix : requestedSize;
        ChangeLogQuery sourceQuery = new ChangeLogQuery();
        org.springframework.beans.BeanUtils.copyProperties(query, sourceQuery);
        if (combined) { sourceQuery.setPage(1); sourceQuery.setPageSize(prefixSize); }

        List<ChangeLogVO> merged = new ArrayList<>();
        long total = 0;

        if (queryLicense) {
            Page<ChangeLogVO> page = new Page<>(sourceQuery.getPage(), sourceQuery.getPageSize());
            page.setMaxLimit((long)sourceQuery.getPageSize()); // Checked internal prefix, not the public page size.
            IPage<ChangeLogVO> result = changeLogMapper.findAllWithLicense(page, sourceQuery);
            for (ChangeLogVO vo : result.getRecords()) {
                vo.setModule("LICENSE");
                vo.setModuleLabel("证照管理");
                vo.setChangeTypeLabel(changeTypeLabel(vo.getChangeType()));
                if (vo.getLicenseType() != null && !vo.getLicenseType().isEmpty()) {
                    try {
                        vo.setLicenseTypeLabel(LicenseTypeEnum.valueOf(vo.getLicenseType()).getLabel());
                    } catch (IllegalArgumentException e) {
                        vo.setLicenseTypeLabel(vo.getLicenseType());
                    }
                }
            }
            merged.addAll(result.getRecords());
            total += result.getTotal();
        }

        if (queryCatalog) {
            PageVO<ChangeLogVO> result = catalogService.pageChangeLogs(sourceQuery);
            for (ChangeLogVO vo : result.getRecords()) {
                vo.setModule("CATALOG");
                vo.setModuleLabel("试剂目录");
                vo.setChangeTypeLabel(catalogChangeTypeLabel(vo.getChangeType()));
                vo.setNote(vo.getNote()); // change_reason already mapped as note
                if (vo.getReviewStatus() != null) {
                    vo.setReviewStatusLabel(switch (vo.getReviewStatus()) {
                        case "PENDING" -> "待审批";
                        case "APPROVED" -> "已通过";
                        case "REJECTED" -> "已驳回";
                        default -> vo.getReviewStatus();
                    });
                }
            }
            merged.addAll(result.getRecords());
            total += result.getTotal();
        }

        // 多模块时按时间倒序排序并截取
        if (queryLicense && queryCatalog) {
            merged.sort((a, b) -> {
                if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                if (a.getCreatedAt() == null) return 1;
                if (b.getCreatedAt() == null) return -1;
                int time = b.getCreatedAt().compareTo(a.getCreatedAt());
                if (time != 0) return time;
                int moduleOrder = a.getModule().compareTo(b.getModule());
                return moduleOrder != 0 ? moduleOrder : b.getId().compareTo(a.getId());
            });
            // 截取当前页
            int start = (query.getPage() - 1) * query.getPageSize();
            int end = Math.min(start + query.getPageSize(), merged.size());
            if (start < merged.size()) {
                merged = merged.subList(start, end);
            } else {
                merged = new ArrayList<>();
            }
        }

        return new PageVO<>(total, query.getPage(), query.getPageSize(), merged);
    }

    // ─── 私有方法 ───

    private LicenseVO toVO(SupplierLicense license) {
        var supplier = supplierMapper.selectById(license.getSupplierId());
        var today = LocalDate.now();
        var effectiveStatus = LicenseExpiryPolicy.status(license.getStatus(), license.getExpireDate(), today);
        String reminder = LicenseExpiryPolicy.reminderLevel(license.getStatus(), license.getExpireDate(), today);
        return LicenseVO.builder()
                .id(license.getId())
                .supplierId(license.getSupplierId())
                .supplierName(supplier != null ? supplier.getName() : "")
                .licenseType(license.getLicenseType().name())
                .licenseTypeLabel(license.getLicenseType().getLabel())
                .certNo(license.getCertNo())
                .issuingAuthority(license.getIssuingAuthority())
                .issueDate(license.getIssueDate())
                .expireDate(license.getExpireDate())
                .status(effectiveStatus.name())
                .statusLabel(effectiveStatus.getLabel())
                .remainingDays(LicenseExpiryPolicy.remainingDays(license.getExpireDate(), today))
                .reminderLevel(reminder)
                .reminderLabel(LicenseExpiryPolicy.reminderLabel(reminder))
                .attachmentCount(parseAttachments(license.getAttachments()).size())
                .createdAt(license.getCreatedAt())
                .build();
    }

    private void validateLicenseDates(LocalDate issueDate, LocalDate expireDate) {
        if (issueDate == null || expireDate == null || issueDate.isAfter(expireDate))
            throw new BizException(40001, "发证日期和有效期必填，且发证日期不得晚于有效期");
    }

    private LicenseStatusEnum calcStatus(LocalDate expireDate) {
        return LicenseExpiryPolicy.status(null, expireDate, LocalDate.now());
    }

    private void applyEffectiveStatusFilter(LambdaQueryWrapper<SupplierLicense> query, String status) {
        LocalDate today = LocalDate.now();
        switch (status) {
            case "FROZEN" -> query.eq(SupplierLicense::getStatus, LicenseStatusEnum.FROZEN);
            case "EXPIRED" -> query.ne(SupplierLicense::getStatus, LicenseStatusEnum.FROZEN)
                    .and(expiry -> expiry.eq(SupplierLicense::getStatus, LicenseStatusEnum.EXPIRED)
                            .or().isNull(SupplierLicense::getExpireDate).or().le(SupplierLicense::getExpireDate, today));
            case "EXPIRING" -> query.notIn(SupplierLicense::getStatus, LicenseStatusEnum.FROZEN,LicenseStatusEnum.EXPIRED)
                    .gt(SupplierLicense::getExpireDate, today).le(SupplierLicense::getExpireDate, today.plusDays(30));
            case "ACTIVE" -> query.notIn(SupplierLicense::getStatus, LicenseStatusEnum.FROZEN,LicenseStatusEnum.EXPIRED)
                    .gt(SupplierLicense::getExpireDate, today.plusDays(30));
            default -> throw new BizException(40001, "证照状态无效");
        }
    }

    private List<LicenseDetailVO.RelReagent> relatedReagents(Long supplierId) {
        var relations = reagentSupplierRelMapper.selectList(
                new LambdaQueryWrapper<com.zlth.ivd.license.entity.ReagentSupplierRel>()
                        .eq(com.zlth.ivd.license.entity.ReagentSupplierRel::getSupplierId, supplierId));
        Map<Long, LicenseDetailVO.RelReagent> result = new LinkedHashMap<>();
        for (var relation : relations) {
            if (relation.getReagentId() == null) continue;
            try {
                var reagent = catalogService.getById(relation.getReagentId());
                if (reagent == null) continue;
                result.put(reagent.getId(), LicenseDetailVO.RelReagent.builder()
                        .reagentId(reagent.getId()).hisCode(reagent.getHisCode()).genericName(reagent.getGenericName())
                        .manufacturer(reagent.getManufacturer()).isDefault(Boolean.TRUE.equals(relation.getIsDefault())).build());
            } catch (BizException notVisible) {
                if (notVisible.getErrorCode() != 40400 && notVisible.getErrorCode() != 40007) throw notVisible;
                // The relation must not expose a deleted or tenant-hidden catalog entry.
            }
        }
        return new ArrayList<>(result.values());
    }

    private record RenewalProofs(Set<String> keys, boolean uncertain) {}

    private RenewalProofs renewalProofs(List<LicenseChangeLog> history) {
        Set<String> keys = new HashSet<>();
        boolean uncertain = false;
        for (var item : history) {
            if (!"RENEW".equals(item.getChangeType())) continue;
            try {
                var note = OBJECT_MAPPER.readTree(item.getNote());
                var key = note == null ? null : note.get("attachmentKey");
                if (key == null || !key.isTextual() || key.asText().isBlank()) uncertain = true;
                else keys.add(key.asText());
            } catch (Exception malformed) {
                uncertain = true;
            }
        }
        return new RenewalProofs(keys, uncertain);
    }

    private void requireMutableAttachment(Long licenseId, String key) {
        var proofs = renewalProofs(changeLogMapper.findByLicenseId(licenseId));
        if (proofs.uncertain())
            throw new BizException(40900, "续证历史证明信息不完整，不能删除或替换附件，请先人工核对");
        if (proofs.keys().contains(key))
            throw new BizException(40900, "该附件是已批准续证的审核依据，只能阅览，不可删除或替换");
    }

    private void insertChangeLog(Long licenseId, String changeType, LocalDate oldExpire,
                                  LocalDate newExpire, Long operatorId, String operatorName, String note) {
        LicenseChangeLog log = new LicenseChangeLog();
        log.setLicenseId(licenseId);
        log.setChangeType(changeType);
        log.setOldExpireDate(oldExpire);
        log.setNewExpireDate(newExpire);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setNote(note);
        changeLogMapper.insert(log);
    }

    private List<LicenseDetailVO.AttachmentItem> parseAttachments(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<LicenseDetailVO.AttachmentItem>>() {});
        } catch (Exception e) {
            log.error("解析附件JSON失败，附件数据可能已损坏: {}", e.getMessage());
            throw new BizException(50000, "附件数据解析失败，请联系管理员修复");
        }
    }

    @Override
    public void downloadAttachment(Long licenseId, String objectKey, HttpServletResponse response) {
        // 防止路径穿越攻击
        String safeKey = sanitizeFilename(objectKey);
        if (!safeKey.equals(objectKey)) {
            log.warn("附件下载路径穿越攻击检测: licenseId={}, objectKey={}", licenseId, objectKey);
            throw new BizException(40400, "附件文件不存在");
        }

        // 先校验证照归属与当前附件成员关系，再访问存储，禁止通过旧key绕过授权。
        SupplierLicense license = licenseMapper.selectById(licenseId);
        if (license == null) throw new BizException(40400, "附件不存在或无权访问");
        LicenseDetailVO.AttachmentItem attachment = parseAttachments(license.getAttachments()).stream()
                .filter(att -> objectKey.equals(att.getObjectKey())).findFirst()
                .orElseThrow(() -> new BizException(40400, "附件不存在或无权访问"));
        String objectName = "license/" + licenseId + "/" + safeKey;
        String originalName = StringUtils.hasText(attachment.getName()) ? attachment.getName() : objectKey;

        String contentType = guessContentType(originalName);
        boolean inline = Set.of("application/pdf", "image/png", "image/jpeg", "image/gif", "image/bmp", "image/webp")
                .contains(contentType);
        response.setContentType(inline ? contentType : "application/octet-stream");
        response.setHeader("Content-Disposition",
                (inline ? "inline" : "attachment") + "; filename=\"" + URLEncoder.encode(originalName, StandardCharsets.UTF_8) + "\"");
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");

        try (GetObjectResponse getResponse = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioProps.getBucketCert())
                .object(objectName)
                .build());
             InputStream is = getResponse;
             OutputStream os = response.getOutputStream()) {
            StreamUtils.copy(is, os);
            os.flush();
        } catch (ErrorResponseException e) {
            log.warn("附件不存在: licenseId={}, objectKey={}", licenseId, objectKey);
            throw new BizException(40400, "附件文件不存在");
        } catch (InsufficientDataException | IOException e) {
            log.error("附件流式传输失败: licenseId={}, objectKey={}", licenseId, objectKey, e);
            throw new BizException(50000, "附件读取失败");
        } catch (Exception e) {
            log.error("MinIO下载未知异常: licenseId={}, objectKey={}", licenseId, objectKey, e);
            throw new BizException(50000, "附件下载失败: 存储服务异常");
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null) return "unknown";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".png")) return "png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "jpg";
        if (lower.endsWith(".gif")) return "gif";
        if (lower.endsWith(".bmp")) return "bmp";
        if (lower.endsWith(".webp")) return "webp";
        if (lower.endsWith(".svg")) return "svg";
        if (lower.endsWith(".txt")) return "txt";
        if (lower.endsWith(".csv")) return "csv";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".md")) return "md";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "doc";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "xls";
        return "unknown";
    }

    /**
     * 清理文件名中的路径穿越字符，防止目录遍历攻击
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) return "file";
        // 移除路径分隔符和路径穿越序列
        return filename.replaceAll("[/\\\\]+", "_")
                .replaceAll("\\.\\.", "_")
                .replaceAll("\\p{Cntrl}", "")  // 移除控制字符
                .replaceAll("^[.\\s]+", "")     // 去除前导点和空白
                .trim();
    }

    private String guessContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        // 图片
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        // 文档
        if (lower.endsWith(".pdf")) return "application/pdf";
        // 文本
        if (lower.endsWith(".txt")) return "text/plain; charset=utf-8";
        if (lower.endsWith(".csv")) return "text/csv; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".md")) return "text/markdown; charset=utf-8";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        // Office
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return "application/octet-stream";
    }

    private String changeTypeLabel(String type) {
        return switch (type) {
            case "CREATE" -> "新增";
            case "RENEW" -> "续期";
            case "UPDATE" -> "变更";
            case "FREEZE" -> "冻结";
            case "UNFREEZE" -> "解冻";
            case "DELETE_ATTACHMENT" -> "删除附件";
            case "ADD_ATTACHMENT" -> "新增附件";
            case "REPLACE_ATTACHMENT" -> "变更附件";
            default -> type;
        };
    }

    private String catalogChangeTypeLabel(String type) {
        return switch (type) {
            case "CREATE" -> "新增试剂";
            case "UPDATE" -> "编辑试剂";
            case "DELETE" -> "删除试剂";
            case "DISABLE" -> "停用试剂";
            default -> type;
        };
    }

    /** 审批状态中文标签 */
    private String reviewStatusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "待审批";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已驳回";
            default -> status;
        };
    }
}
