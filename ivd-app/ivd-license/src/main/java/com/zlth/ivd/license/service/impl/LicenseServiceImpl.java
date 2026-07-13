package com.zlth.ivd.license.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.enums.LicenseStatusEnum;
import com.zlth.ivd.common.enums.LicenseTypeEnum;
import com.zlth.ivd.common.exception.BizException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
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
        license.setId(IdUtil.getSnowflakeNextId());
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
        // TODO: MinIO 上传实现
        log.info("证照附件上传: licenseId={}, fileName={}, size={}", licenseId, file.getOriginalFilename(), file.getSize());
        return "mock-object-key-" + IdUtil.getSnowflakeNextId();
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
        log.setId(IdUtil.getSnowflakeNextId());
        log.setLicenseId(licenseId);
        log.setChangeType(changeType);
        log.setOldExpireDate(oldExpire);
        log.setNewExpireDate(newExpire);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setNote(note);
        changeLogMapper.insert(log);
    }

    @SuppressWarnings("unchecked")
    private List<LicenseDetailVO.AttachmentItem> parseAttachments(String json) {
        // TODO: 使用 Jackson 解析 JSON
        return Collections.emptyList();
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
