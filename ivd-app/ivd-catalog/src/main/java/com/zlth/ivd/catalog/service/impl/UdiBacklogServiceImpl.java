package com.zlth.ivd.catalog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.catalog.entity.UdiBacklog;
import com.zlth.ivd.catalog.mapper.UdiBacklogMapper;
import com.zlth.ivd.catalog.service.UdiBacklogService;
import com.zlth.ivd.common.enums.MaterialTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * UDI 待补全 Service 实现
 *
 * @author IVD Team
 * @since 2026-07-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UdiBacklogServiceImpl implements UdiBacklogService {

    private final UdiBacklogMapper udiBacklogMapper;

    @Override
    public IPage<UdiBacklog> pageList(int page, int pageSize, String impactLevel) {
        Page<UdiBacklog> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<UdiBacklog> wrapper = new LambdaQueryWrapper<UdiBacklog>()
                .orderByDesc(UdiBacklog::getDaysPending);
        if (impactLevel != null && !impactLevel.isEmpty()) {
            wrapper.eq(UdiBacklog::getImpactLevel, impactLevel);
        }
        return udiBacklogMapper.selectPage(p, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createBacklog(Long reagentId, MaterialTypeEnum materialType, String impactLevel) {
        UdiBacklog backlog = new UdiBacklog();
        backlog.setReagentId(reagentId);
        backlog.setMaterialType(materialType);
        backlog.setImpactLevel(impactLevel);
        backlog.setHasOpenPo(false);
        backlog.setDaysPending(0);
        backlog.setCreatedAt(LocalDateTime.now());
        backlog.setUpdatedAt(LocalDateTime.now());
        udiBacklogMapper.insert(backlog);
        log.info("UDI待补全记录已创建: reagentId={}, materialType={}, impactLevel={}",
                reagentId, materialType.getCode(), impactLevel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByReagentId(Long reagentId) {
        udiBacklogMapper.delete(
                new LambdaQueryWrapper<UdiBacklog>()
                        .eq(UdiBacklog::getReagentId, reagentId)
        );
        log.info("UDI待补全记录已删除: reagentId={}", reagentId);
    }
}
