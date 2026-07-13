package com.zlth.ivd.license.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.license.dto.CreateDistributorRequest;
import com.zlth.ivd.license.dto.UpdateDistributorRequest;
import com.zlth.ivd.license.entity.Distributor;
import com.zlth.ivd.license.mapper.DistributorMapper;
import com.zlth.ivd.license.service.DistributorService;
import com.zlth.ivd.license.vo.DistributorVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 配送商管理服务实现
 *
 * @author IVD Team
 * @since 2026-07-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class DistributorServiceImpl implements DistributorService {

    private final DistributorMapper distributorMapper;

    @Override
    public PageVO<DistributorVO> list(int page, int pageSize, String keyword) {
        LambdaQueryWrapper<Distributor> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Distributor::getName, keyword)
                    .or().like(Distributor::getShortName, keyword)
                    .or().like(Distributor::getContactPerson, keyword));
        }
        qw.orderByDesc(Distributor::getCreatedAt);

        Page<Distributor> p = new Page<>(page, Math.min(pageSize, 200));
        Page<Distributor> result = distributorMapper.selectPage(p, qw);

        List<DistributorVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return new PageVO<>(result.getTotal(), page, pageSize, records);
    }

    @Override
    public DistributorVO getById(Long id) {
        Distributor entity = distributorMapper.selectById(id);
        if (entity == null) {
            throw new BizException(40400, "配送商不存在: id=" + id);
        }
        return toVO(entity);
    }

    @Override
    public DistributorVO create(CreateDistributorRequest request) {
        // 名称唯一性校验
        Long exists = distributorMapper.selectCount(
                new LambdaQueryWrapper<Distributor>()
                        .eq(Distributor::getName, request.getName())
        );
        if (exists != null && exists > 0) {
            throw new BizException(40001, "配送商名称已存在: " + request.getName());
        }

        Distributor entity = new Distributor();
        entity.setName(request.getName());
        entity.setShortName(request.getShortName());
        entity.setContactPerson(request.getContactPerson());
        entity.setContactPhone(request.getContactPhone());
        entity.setContactEmail(request.getContactEmail());
        entity.setAddress(request.getAddress());
        entity.setIsActive(true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        distributorMapper.insert(entity);
        log.info("配送商已创建: id={}, name={}", entity.getId(), entity.getName());
        return toVO(entity);
    }

    @Override
    public DistributorVO update(Long id, UpdateDistributorRequest request) {
        Distributor entity = distributorMapper.selectById(id);
        if (entity == null) {
            throw new BizException(40400, "配送商不存在: id=" + id);
        }

        if (StringUtils.hasText(request.getName())) {
            entity.setName(request.getName());
        }
        if (request.getShortName() != null) {
            entity.setShortName(request.getShortName());
        }
        if (request.getContactPerson() != null) {
            entity.setContactPerson(request.getContactPerson());
        }
        if (request.getContactPhone() != null) {
            entity.setContactPhone(request.getContactPhone());
        }
        if (request.getContactEmail() != null) {
            entity.setContactEmail(request.getContactEmail());
        }
        if (request.getAddress() != null) {
            entity.setAddress(request.getAddress());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }

        distributorMapper.updateById(entity);
        log.info("配送商已编辑: id={}, name={}", id, entity.getName());
        return toVO(entity);
    }

    @Override
    public void delete(Long id) {
        Distributor entity = distributorMapper.selectById(id);
        if (entity == null) {
            throw new BizException(40400, "配送商不存在: id=" + id);
        }
        distributorMapper.deleteById(id);
        log.info("配送商已删除: id={}, name={}", id, entity.getName());
    }

    @Override
    public void toggleActive(Long id) {
        Distributor entity = distributorMapper.selectById(id);
        if (entity == null) {
            throw new BizException(40400, "配送商不存在: id=" + id);
        }
        entity.setIsActive(!entity.getIsActive());
        distributorMapper.updateById(entity);
        log.info("配送商状态切换: id={}, name={}, isActive={}", id, entity.getName(), entity.getIsActive());
    }

    @Override
    public List<DistributorVO> options(String keyword) {
        LambdaQueryWrapper<Distributor> qw = new LambdaQueryWrapper<>();
        qw.eq(Distributor::getIsActive, true);
        if (StringUtils.hasText(keyword)) {
            qw.like(Distributor::getName, keyword);
        }
        qw.orderByAsc(Distributor::getName);
        return distributorMapper.selectList(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<DistributorVO> exportAll() {
        return distributorMapper.selectList(
                new LambdaQueryWrapper<Distributor>().orderByDesc(Distributor::getCreatedAt)
        ).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public int importBatch(List<CreateDistributorRequest> requests) {
        int count = 0;
        for (CreateDistributorRequest req : requests) {
            try {
                create(req);
                count++;
            } catch (BizException e) {
                log.warn("导入配送商跳过: {} — {}", req.getName(), e.getMessage());
            }
        }
        return count;
    }

    private DistributorVO toVO(Distributor entity) {
        DistributorVO vo = new DistributorVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setShortName(entity.getShortName());
        vo.setContactPerson(entity.getContactPerson());
        vo.setContactPhone(entity.getContactPhone());
        vo.setContactEmail(entity.getContactEmail());
        vo.setAddress(entity.getAddress());
        vo.setIsActive(entity.getIsActive());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
