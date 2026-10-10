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
import com.zlth.ivd.catalog.mapper.CatalogApprovalLogMapper;
import com.zlth.ivd.license.entity.SupplierLicense;
import com.zlth.ivd.license.mapper.LicenseChangeLogMapper;
import com.zlth.ivd.license.mapper.ReagentSupplierRelMapper;
import com.zlth.ivd.license.mapper.SupplierLicenseMapper;
import com.zlth.ivd.license.mapper.SupplierMapper;
import com.zlth.ivd.license.service.LicenseService;
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
    private final CatalogApprovalLogMapper catalogApprovalLogMapper;
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
            qw.eq(SupplierLicense::getStatus, LicenseStatusEnum.valueOf(query.getStatus()));
        }
        // 关键词搜索：通过 join 在 SQL 中处理，这里简化
        qw.orderByDesc(SupplierLicense::getCreatedAt);

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
                .status(license.getStatus().name())
                .statusLabel(license.getStatus().getLabel())
                .remainingDays(ChronoUnit.DAYS.between(LocalDate.now(), license.getExpireDate()))
                .relatedReagents(Collections.emptyList()) // TODO: 联表查询
                .attachments(parseAttachments(license.getAttachments()))
                .changeLogs(changeItems)
                .createdAt(license.getCreatedAt())
                .updatedAt(license.getUpdatedAt())
                .build();
    }

    @Override
    public LicenseVO create(CreateLicenseRequest request) {
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
                request.getOperatorId(), request.getOperatorName(), "创建证照");

        return toVO(license);
    }

    @Override
    public LicenseVO update(Long id, UpdateLicenseRequest request) {
        SupplierLicense license = licenseMapper.selectById(id);
        if (license == null) {
            throw new BizException(40400, "证照不存在");
        }

        LocalDate oldExpireDate = license.getExpireDate();

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
            license.setStatus(calcStatus(request.getExpireDate()));
        }
        if (StringUtils.hasText(request.getStatus())) {
            license.setStatus(LicenseStatusEnum.valueOf(request.getStatus()));
        }

        licenseMapper.updateById(license);

        // 记录变更日志
        insertChangeLog(license.getId(), "UPDATE", oldExpireDate,
                request.getExpireDate() != null ? request.getExpireDate() : oldExpireDate,
                request.getOperatorId(), request.getOperatorName(),
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
                    return m;
                }).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<LicenseVO> getExpiryAlerts(int days) {
        return licenseMapper.findExpiringWithinDays(days).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public String uploadAttachment(Long licenseId, MultipartFile file, Long operatorId) {
        SupplierLicense license = licenseMapper.selectById(licenseId);
        if (license == null) {
            throw new BizException(40400, "证照不存在");
        }

        // 生成唯一对象名，并清理路径穿越字符
        String originalName = file.getOriginalFilename();
        String safeName = sanitizeFilename(originalName != null ? originalName : "file");
        String objectKey = System.currentTimeMillis() + "_" + safeName;

        // MinIO 对象路径: license/{licenseId}/{objectKey}
        String objectName = "license/" + licenseId + "/" + objectKey;
        String contentType = guessContentType(originalName);

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
        List<LicenseDetailVO.AttachmentItem> attachments = parseAttachments(license.getAttachments());
        attachments.add(item);

        try {
            license.setAttachments(OBJECT_MAPPER.writeValueAsString(attachments));
        } catch (Exception e) {
            log.error("附件JSON序列化失败: licenseId={}", licenseId, e);
            throw new BizException(50000, "附件信息保存失败");
        }
        licenseMapper.updateById(license);

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
        List<SupplierLicense> expiring = licenseMapper.findExpiringWithinDays(90);
        for (SupplierLicense lic : expiring) {
            LicenseStatusEnum newStatus = calcStatus(lic.getExpireDate());
            if (newStatus != lic.getStatus()) {
                lic.setStatus(newStatus);
                licenseMapper.updateById(lic);
                log.info("[XXL-Job] 证照 {} 状态更新: {} -> {}", lic.getId(), lic.getStatus(), newStatus);
            }
        }
        log.info("[XXL-Job] 证照效期检查完成，处理 {} 条", expiring.size());
    }

    @Override
    public void deleteAttachment(Long licenseId, String objectKey, Long operatorId) {
        SupplierLicense license = licenseMapper.selectById(licenseId);
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

        // 1. 删除 MinIO 对象
        String objectName = "license/" + licenseId + "/" + objectKey;
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProps.getBucketCert())
                    .object(objectName)
                    .build());
        } catch (ErrorResponseException e) {
            log.warn("MinIO删除附件失败(对象可能已不存在): licenseId={}, objectKey={}", licenseId, objectKey, e);
        } catch (Exception e) {
            log.error("MinIO删除附件异常: licenseId={}, objectKey={}", licenseId, objectKey, e);
            throw new BizException(50000, "附件删除失败: 存储服务异常");
        }

        // 2. 从 JSON 中移除
        attachments.remove(target);
        try {
            license.setAttachments(OBJECT_MAPPER.writeValueAsString(attachments));
        } catch (Exception e) {
            log.error("附件JSON序列化失败: licenseId={}", licenseId, e);
            throw new BizException(50000, "附件信息保存失败");
        }
        licenseMapper.updateById(license);

        // 3. 记录变更日志
        String operatorName = SecurityUtils.getCurrentUsername();
        String note = String.format("{\"fileName\":\"%s\",\"objectKey\":\"%s\"}", target.getName(), target.getObjectKey());
        insertChangeLog(licenseId, "DELETE_ATTACHMENT", null, null, operatorId, operatorName, note);

        log.info("证照附件已删除: licenseId={}, objectKey={}, fileName={}", licenseId, objectKey, target.getName());
    }

    @Override
    public String replaceAttachment(Long licenseId, String oldObjectKey, MultipartFile file, Long operatorId) {
        SupplierLicense license = licenseMapper.selectById(licenseId);
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

        String oldFileName = oldItem.getName();

        // 1. 上传新文件到 MinIO
        String originalName = file.getOriginalFilename();
        String safeName = sanitizeFilename(originalName != null ? originalName : "file");
        String newObjectKey = System.currentTimeMillis() + "_" + safeName;
        String newObjectName = "license/" + licenseId + "/" + newObjectKey;
        String contentType = guessContentType(originalName);

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

        // 3. 删除 MinIO 旧对象
        String oldObjectName = "license/" + licenseId + "/" + oldObjectKey;
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProps.getBucketCert())
                    .object(oldObjectName)
                    .build());
        } catch (ErrorResponseException e) {
            log.warn("MinIO删除旧附件失败(对象可能已不存在): licenseId={}, oldObjectKey={}", licenseId, oldObjectKey, e);
        } catch (Exception e) {
            log.error("MinIO删除旧附件异常: licenseId={}, oldObjectKey={}", licenseId, oldObjectKey, e);
            // 新文件已上传成功，旧文件删除失败不阻断流程
        }

        // 4. 写回 DB
        try {
            license.setAttachments(OBJECT_MAPPER.writeValueAsString(attachments));
        } catch (Exception e) {
            log.error("附件JSON序列化失败: licenseId={}", licenseId, e);
            throw new BizException(50000, "附件信息保存失败");
        }
        licenseMapper.updateById(license);

        // 5. 记录变更日志
        String operatorName = SecurityUtils.getCurrentUsername();
        String note = String.format("{\"oldFileName\":\"%s\",\"newFileName\":\"%s\",\"oldKey\":\"%s\",\"newKey\":\"%s\"}",
                oldFileName, oldItem.getName(), oldObjectKey, newObjectKey);
        insertChangeLog(licenseId, "REPLACE_ATTACHMENT", null, null, operatorId, operatorName, note);

        log.info("证照附件已替换: licenseId={}, oldKey={} -> newKey={}, newFile={}",
                licenseId, oldObjectKey, newObjectKey, originalName);
        return newObjectKey;
    }

    @Override
    public PageVO<ChangeLogVO> listChangeLogs(ChangeLogQuery query) {
        String module = query.getModule();
        boolean queryLicense = module == null || module.isEmpty() || "LICENSE".equalsIgnoreCase(module);
        boolean queryCatalog = module == null || module.isEmpty() || "CATALOG".equalsIgnoreCase(module);

        List<ChangeLogVO> merged = new ArrayList<>();
        long total = 0;

        if (queryLicense) {
            Page<ChangeLogVO> page = new Page<>(query.getPage(), query.getPageSize());
            IPage<ChangeLogVO> result = changeLogMapper.findAllWithLicense(page, query);
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
            Page<ChangeLogVO> page = new Page<>(query.getPage(), query.getPageSize());
            IPage<ChangeLogVO> result = catalogApprovalLogMapper.findAllWithReagent(page, query);
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
                return b.getCreatedAt().compareTo(a.getCreatedAt());
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
        long remainingDays = ChronoUnit.DAYS.between(LocalDate.now(), license.getExpireDate());
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
                .status(license.getStatus().name())
                .statusLabel(license.getStatus().getLabel())
                .remainingDays(remainingDays)
                .attachmentCount(parseAttachments(license.getAttachments()).size())
                .createdAt(license.getCreatedAt())
                .build();
    }

    private LicenseStatusEnum calcStatus(LocalDate expireDate) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), expireDate);
        if (days <= 0) return LicenseStatusEnum.EXPIRED;
        if (days <= 90) return LicenseStatusEnum.EXPIRING;
        return LicenseStatusEnum.ACTIVE;
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

        // MinIO 对象路径
        String objectName = "license/" + licenseId + "/" + safeKey;

        // 从附件记录中获取原始文件名
        SupplierLicense license = licenseMapper.selectById(licenseId);
        String originalName = objectKey;
        if (license != null && StringUtils.hasText(license.getAttachments())) {
            List<LicenseDetailVO.AttachmentItem> attachments = parseAttachments(license.getAttachments());
            for (LicenseDetailVO.AttachmentItem att : attachments) {
                if (objectKey.equals(att.getObjectKey())) {
                    originalName = att.getName();
                    break;
                }
            }
        }

        String contentType = guessContentType(originalName);
        response.setContentType(contentType);
        response.setHeader("Content-Disposition",
                "inline; filename=\"" + URLEncoder.encode(originalName, StandardCharsets.UTF_8) + "\"");
        response.setHeader("Cache-Control", "max-age=86400");

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
