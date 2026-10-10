package com.zlth.ivd.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.catalog.mapper.ReagentCatalogMapper;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.system.dto.CatalogFieldOptionRequest;
import com.zlth.ivd.system.entity.CatalogFieldOption;
import com.zlth.ivd.system.enums.CatalogFieldCodeEnum;
import com.zlth.ivd.system.mapper.CatalogFieldOptionMapper;
import com.zlth.ivd.system.service.CatalogFieldOptionService;
import com.zlth.ivd.system.vo.CatalogFieldOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class CatalogFieldOptionServiceImpl implements CatalogFieldOptionService {
    private final CatalogFieldOptionMapper optionMapper;
    private final ReagentCatalogMapper catalogMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CatalogFieldOptionVO> list(String fieldCode, boolean includeDisabled) {
        if (fieldCode != null && !fieldCode.isBlank()) CatalogFieldCodeEnum.fromCode(fieldCode);
        LambdaQueryWrapper<CatalogFieldOption> query = new LambdaQueryWrapper<CatalogFieldOption>()
                .eq(fieldCode != null && !fieldCode.isBlank(), CatalogFieldOption::getFieldCode, fieldCode)
                .eq(!includeDisabled, CatalogFieldOption::getStatus, 1)
                .orderByAsc(CatalogFieldOption::getFieldCode)
                .orderByAsc(CatalogFieldOption::getSortOrder)
                .orderByAsc(CatalogFieldOption::getId);
        return optionMapper.selectList(query).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<CatalogFieldOptionVO>> activeOptions() {
        Map<String, List<CatalogFieldOptionVO>> grouped = new LinkedHashMap<>();
        for (CatalogFieldCodeEnum type : CatalogFieldCodeEnum.values()) grouped.put(type.name(), List.of());
        grouped.putAll(list(null, false).stream().collect(Collectors.groupingBy(
                CatalogFieldOptionVO::getFieldCode, LinkedHashMap::new, Collectors.toList())));
        return grouped;
    }

    @Override
    public CatalogFieldOptionVO create(CatalogFieldOptionRequest request) {
        CatalogFieldCodeEnum.fromCode(request.getFieldCode());
        ensureUnique(null, request.getFieldCode(), request.getOptionValue());
        CatalogFieldOption option = new CatalogFieldOption();
        copyRequest(request, option);
        option.setStatus(1);
        option.setCreatedAt(LocalDateTime.now());
        option.setUpdatedAt(LocalDateTime.now());
        optionMapper.insert(option);
        return toVO(option);
    }

    @Override
    public CatalogFieldOptionVO update(Long id, CatalogFieldOptionRequest request) {
        CatalogFieldCodeEnum.fromCode(request.getFieldCode());
        CatalogFieldOption option = required(id);
        boolean identityChanged = !Objects.equals(option.getFieldCode(), request.getFieldCode())
                || !Objects.equals(option.getOptionValue(), request.getOptionValue());
        if (identityChanged && isUsed(option)) {
            throw new BizException(40900, "该选项已被试剂目录使用，只能修改显示名称、排序、备注或停用");
        }
        ensureUnique(id, request.getFieldCode(), request.getOptionValue());
        copyRequest(request, option);
        option.setUpdatedAt(LocalDateTime.now());
        optionMapper.updateById(option);
        return toVO(option);
    }

    @Override
    public void toggleStatus(Long id) {
        CatalogFieldOption option = required(id);
        option.setStatus(option.getStatus() != null && option.getStatus() == 1 ? 0 : 1);
        option.setUpdatedAt(LocalDateTime.now());
        optionMapper.updateById(option);
    }

    @Override
    public void delete(Long id) {
        CatalogFieldOption option = required(id);
        if (isUsed(option)) throw new BizException(40900, "该选项已被试剂目录使用，不能删除，可改为停用");
        optionMapper.physicalDeleteById(id);
    }

    private void ensureUnique(Long id, String fieldCode, String optionValue) {
        LambdaQueryWrapper<CatalogFieldOption> query = new LambdaQueryWrapper<CatalogFieldOption>()
                .eq(CatalogFieldOption::getFieldCode, fieldCode)
                .eq(CatalogFieldOption::getOptionValue, optionValue)
                .ne(id != null, CatalogFieldOption::getId, id);
        if (optionMapper.selectCount(query) > 0) throw new BizException(40900, "该字段分类下已存在相同选项值");
    }

    private boolean isUsed(CatalogFieldOption option) {
        LambdaQueryWrapper<ReagentCatalog> query = new LambdaQueryWrapper<>();
        switch (CatalogFieldCodeEnum.fromCode(option.getFieldCode())) {
            case TEST_GROUP -> query.like(ReagentCatalog::getTestGroups, option.getOptionValue());
            case DETECTION_METHOD -> query.eq(ReagentCatalog::getDetectionMethod, option.getOptionValue());
            case VOLUME_TYPE -> query.eq(ReagentCatalog::getVolumeType, option.getOptionValue());
            case INSURANCE_PAYMENT_TYPE -> query.eq(ReagentCatalog::getInsurancePaymentType, option.getOptionValue());
        }
        return catalogMapper.selectCount(query) > 0;
    }

    private CatalogFieldOption required(Long id) {
        CatalogFieldOption option = optionMapper.selectById(id);
        if (option == null) throw new BizException(40400, "字段配置项不存在: id=" + id);
        return option;
    }

    private void copyRequest(CatalogFieldOptionRequest request, CatalogFieldOption option) {
        option.setFieldCode(request.getFieldCode());
        option.setOptionLabel(request.getOptionLabel().trim());
        option.setOptionValue(request.getOptionValue().trim());
        option.setSortOrder(request.getSortOrder() == null ? 99 : request.getSortOrder());
        option.setRemark(request.getRemark());
    }

    private CatalogFieldOptionVO toVO(CatalogFieldOption option) {
        CatalogFieldOptionVO vo = new CatalogFieldOptionVO();
        vo.setId(option.getId());
        vo.setFieldCode(option.getFieldCode());
        vo.setFieldLabel(CatalogFieldCodeEnum.fromCode(option.getFieldCode()).getLabel());
        vo.setOptionLabel(option.getOptionLabel());
        vo.setOptionValue(option.getOptionValue());
        vo.setSortOrder(option.getSortOrder());
        vo.setStatus(option.getStatus());
        vo.setRemark(option.getRemark());
        vo.setCreatedAt(option.getCreatedAt());
        vo.setUpdatedAt(option.getUpdatedAt());
        return vo;
    }
}
