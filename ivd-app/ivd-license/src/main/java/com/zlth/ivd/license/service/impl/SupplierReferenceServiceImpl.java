package com.zlth.ivd.license.service.impl;

import com.zlth.ivd.license.entity.Supplier;
import com.zlth.ivd.license.mapper.SupplierMapper;
import com.zlth.ivd.license.service.SupplierReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import com.zlth.ivd.license.mapper.SupplierLicenseMapper;
import com.zlth.ivd.license.entity.SupplierLicense;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.common.enums.LicenseStatusEnum;
import com.zlth.ivd.common.exception.BizException;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierReferenceServiceImpl implements SupplierReferenceService {
    private final SupplierMapper mapper;
    private final SupplierLicenseMapper licenses;
    public Supplier findById(Long id) { return mapper.selectById(id); }
    public List<Supplier> findByIds(Collection<Long> ids) { return ids == null || ids.isEmpty() ? List.of() : mapper.selectBatchIds(ids); }
    public List<Supplier> findAll() { return mapper.selectList(null); }
    @Override @Transactional(rollbackFor = Exception.class)
    public void requireProcurementEligible(Long supplierId) {
        if (supplierId == null) throw new BizException(40001, "请选择供应商");
        var supplier = mapper.selectOne(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getId, supplierId).last("FOR UPDATE"));
        if (supplier == null || !Boolean.TRUE.equals(supplier.getIsActive()))
            throw new BizException(40001, "供应商不存在、未启用或不可访问");
        var credentials = licenses.selectList(new LambdaQueryWrapper<SupplierLicense>()
                .eq(SupplierLicense::getSupplierId, supplierId).last("FOR UPDATE"));
        if (credentials.isEmpty()) throw new BizException(40001, "供应商未维护证照，采购已拒绝");
        var today = java.time.LocalDate.now();
        if (credentials.stream().anyMatch(license -> license.getStatus() == null
                || license.getStatus() == LicenseStatusEnum.FROZEN || license.getStatus() == LicenseStatusEnum.EXPIRED
                || license.getIssueDate() == null || license.getIssueDate().isAfter(today)
                || license.getExpireDate() == null || !license.getExpireDate().isAfter(today)))
            throw new BizException(40001, "供应商证照无效、到期或已冻结，请完成续证审批后采购");
    }
}
