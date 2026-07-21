package com.zlth.ivd.license.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.enums.LicenseStatusEnum;
import com.zlth.ivd.common.enums.LicenseTypeEnum;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.utils.SecurityUtils;
import com.zlth.ivd.license.dto.CreateLicenseRequest;
import com.zlth.ivd.license.dto.LicenseQuery;
import com.zlth.ivd.license.dto.UpdateLicenseRequest;
import com.zlth.ivd.license.entity.LicenseChangeLog;
import com.zlth.ivd.license.entity.SupplierLicense;
import com.zlth.ivd.license.mapper.LicenseChangeLogMapper;
import com.zlth.ivd.license.mapper.SupplierLicenseMapper;
import com.zlth.ivd.license.mapper.SupplierMapper;
import com.zlth.ivd.license.service.LicenseService;
import com.zlth.ivd.license.vo.LicenseDetailVO;
import com.zlth.ivd.license.vo.LicenseVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${ivd.upload.path:data/uploads}")
    private String uploadBasePath;

    @Override
    public PageVO<LicenseVO> listLicenses(LicenseQuery query) {
        LambdaQueryWrapper<SupplierLicense> qw = new LambdaQueryWrapper<>();
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

        // 本地存储路径: {ivd.upload.path}/license/{licenseId}/
        String uploadDir = uploadBasePath + File.separator + "license" + File.separator + licenseId;
        File dir = new File(uploadDir);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), uploadDir);
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 生成唯一文件名（防冲突），并清理路径穿越字符
        String originalName = file.getOriginalFilename();
        String safeName = sanitizeFilename(originalName != null ? originalName : "file");
        String objectKey = System.currentTimeMillis() + "_" + safeName;

        try {
            File dest = new File(dir, objectKey);
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("附件保存失败: licenseId={}, fileName={}", licenseId, originalName, e);
            throw new BizException(50000, "附件保存失败: " + e.getMessage());
        }

        // 构建附件条目
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

        log.info("证照附件上传成功: licenseId={}, objectKey={}, fileName={}", licenseId, objectKey, originalName);
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
        String relativePath = uploadBasePath + File.separator + "license" + File.separator + licenseId + File.separator + safeKey;
        Path path = Paths.get(relativePath);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir"), relativePath);
        }
        // 二次校验：确保规范化后的路径仍在预期的上传目录内
        Path uploadDir = Paths.get(uploadBasePath).toAbsolutePath().normalize();
        if (!path.toAbsolutePath().normalize().startsWith(uploadDir)) {
            log.warn("路径穿越攻击检测: licenseId={}, objectKey={}", licenseId, objectKey);
            throw new BizException(40400, "附件文件不存在");
        }
        if (!Files.exists(path)) {
            throw new BizException(40400, "附件文件不存在");
        }

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

        try (FileInputStream fis = new FileInputStream(path.toFile());
             OutputStream os = response.getOutputStream()) {
            StreamUtils.copy(fis, os);
            os.flush();
        } catch (IOException e) {
            log.error("下载附件失败: {}", relativePath, e);
            throw new BizException(50000, "附件读取失败");
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
            default -> type;
        };
    }
}
