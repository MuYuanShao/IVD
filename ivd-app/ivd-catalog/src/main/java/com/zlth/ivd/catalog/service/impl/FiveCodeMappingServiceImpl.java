package com.zlth.ivd.catalog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.catalog.entity.FiveCodeMapping;
import com.zlth.ivd.catalog.mapper.FiveCodeMappingMapper;
import com.zlth.ivd.catalog.service.FiveCodeMappingService;
import com.zlth.ivd.common.enums.FiveCodeMappingStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 五码映射 Service 实现
 *
 * @author IVD Team
 * @since 2026-07-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FiveCodeMappingServiceImpl implements FiveCodeMappingService {

    private final FiveCodeMappingMapper fiveCodeMappingMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FiveCodeMapping createMapping(Long reagentId, String hisCode, String udiDi,
                                          String gtin, String insuranceCode, String nmpaRegNo) {
        FiveCodeMapping mapping = new FiveCodeMapping();
        mapping.setReagentId(reagentId);
        mapping.setHisCode(hisCode);
        mapping.setUdiDi(udiDi != null ? udiDi : "");
        mapping.setGtin(gtin);
        mapping.setInsuranceCode(insuranceCode);
        mapping.setNmpaRegNo(nmpaRegNo);
        mapping.setMappingStatus(FiveCodeMappingStatusEnum.PENDING_MAPPING);
        mapping.setCreatedAt(LocalDateTime.now());
        mapping.setUpdatedAt(LocalDateTime.now());

        fiveCodeMappingMapper.insert(mapping);
        log.info("五码映射已创建: reagentId={}", reagentId);
        return mapping;
    }

    @Override
    public FiveCodeMapping getByReagentId(Long reagentId) {
        return fiveCodeMappingMapper.selectOne(
                new LambdaQueryWrapper<FiveCodeMapping>()
                        .eq(FiveCodeMapping::getReagentId, reagentId)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMapping(Long reagentId, String udiDi, String gtin,
                               String insuranceCode, String nmpaRegNo) {
        FiveCodeMapping mapping = getByReagentId(reagentId);
        if (mapping == null) {
            log.warn("五码映射不存在，跳过更新: reagentId={}", reagentId);
            return;
        }
        boolean changed = false;
        if (udiDi != null && !udiDi.equals(mapping.getUdiDi())) {
            mapping.setUdiDi(udiDi.isEmpty() ? "" : udiDi);
            changed = true;
        }
        if (gtin != null && !gtin.equals(mapping.getGtin())) {
            mapping.setGtin(gtin);
            changed = true;
        }
        if (insuranceCode != null && !insuranceCode.equals(mapping.getInsuranceCode())) {
            mapping.setInsuranceCode(insuranceCode);
            changed = true;
        }
        if (nmpaRegNo != null && !nmpaRegNo.equals(mapping.getNmpaRegNo())) {
            mapping.setNmpaRegNo(nmpaRegNo);
            changed = true;
        }
        if (changed) {
            mapping.setMappingStatus(FiveCodeMappingStatusEnum.PENDING_MAPPING);
            mapping.setUpdatedAt(LocalDateTime.now());
            fiveCodeMappingMapper.updateById(mapping);
            log.info("五码映射已更新: reagentId={}", reagentId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByReagentId(Long reagentId) {
        fiveCodeMappingMapper.delete(
                new LambdaQueryWrapper<FiveCodeMapping>()
                        .eq(FiveCodeMapping::getReagentId, reagentId)
        );
        log.info("五码映射已删除: reagentId={}", reagentId);
    }
}
