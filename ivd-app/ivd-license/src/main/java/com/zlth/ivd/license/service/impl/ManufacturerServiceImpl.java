package com.zlth.ivd.license.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.license.dto.CreateManufacturerRequest;
import com.zlth.ivd.license.dto.UpdateManufacturerRequest;
import com.zlth.ivd.license.entity.Manufacturer;
import com.zlth.ivd.license.mapper.ManufacturerMapper;
import com.zlth.ivd.license.service.ManufacturerService;
import com.zlth.ivd.license.vo.ManufacturerVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 厂家管理服务实现
 *
 * @author IVD Team
 * @since 2026-07-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ManufacturerServiceImpl implements ManufacturerService {

    private final ManufacturerMapper manufacturerMapper;

    @Override
    public PageVO<ManufacturerVO> list(int page, int pageSize, String keyword) {
        LambdaQueryWrapper<Manufacturer> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Manufacturer::getName, keyword)
                    .or().like(Manufacturer::getShortName, keyword)
                    .or().like(Manufacturer::getContactPerson, keyword));
        }
        qw.orderByDesc(Manufacturer::getCreatedAt);

        Page<Manufacturer> p = new Page<>(page, Math.min(pageSize, 200));
        Page<Manufacturer> result = manufacturerMapper.selectPage(p, qw);

        List<ManufacturerVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return new PageVO<>(result.getTotal(), page, pageSize, records);
    }

    @Override
    public ManufacturerVO getById(Long id) {
        Manufacturer entity = manufacturerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(40400, "厂家不存在: id=" + id);
        }
        return toVO(entity);
    }

    @Override
    public ManufacturerVO create(CreateManufacturerRequest request) {
        // 名称唯一性校验
        Long exists = manufacturerMapper.selectCount(
                new LambdaQueryWrapper<Manufacturer>()
                        .eq(Manufacturer::getName, request.getName())
        );
        if (exists != null && exists > 0) {
            throw new BizException(40001, "厂家名称已存在: " + request.getName());
        }

        Manufacturer entity = new Manufacturer();
        entity.setName(request.getName());
        entity.setShortName(request.getShortName());
        entity.setContactPerson(request.getContactPerson());
        entity.setContactPhone(request.getContactPhone());
        entity.setContactEmail(request.getContactEmail());
        entity.setAddress(request.getAddress());
        entity.setIsActive(true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        manufacturerMapper.insert(entity);
        log.info("厂家已创建: id={}, name={}", entity.getId(), entity.getName());
        return toVO(entity);
    }

    @Override
    public ManufacturerVO update(Long id, UpdateManufacturerRequest request) {
        Manufacturer entity = manufacturerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(40400, "厂家不存在: id=" + id);
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

        manufacturerMapper.updateById(entity);
        log.info("厂家已编辑: id={}, name={}", id, entity.getName());
        return toVO(entity);
    }

    @Override
    public void delete(Long id) {
        Manufacturer entity = manufacturerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(40400, "厂家不存在: id=" + id);
        }
        manufacturerMapper.deleteById(id);
        log.info("厂家已删除: id={}, name={}", id, entity.getName());
    }

    @Override
    public void toggleActive(Long id) {
        Manufacturer entity = manufacturerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(40400, "厂家不存在: id=" + id);
        }
        entity.setIsActive(!entity.getIsActive());
        manufacturerMapper.updateById(entity);
        log.info("厂家状态切换: id={}, name={}, isActive={}", id, entity.getName(), entity.getIsActive());
    }

    @Override
    public List<ManufacturerVO> options(String keyword) {
        LambdaQueryWrapper<Manufacturer> qw = new LambdaQueryWrapper<>();
        qw.eq(Manufacturer::getIsActive, true);
        if (StringUtils.hasText(keyword)) {
            qw.like(Manufacturer::getName, keyword);
        }
        qw.orderByAsc(Manufacturer::getName);
        return manufacturerMapper.selectList(qw).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ManufacturerVO> exportAll() {
        return manufacturerMapper.selectList(
                new LambdaQueryWrapper<Manufacturer>().orderByDesc(Manufacturer::getCreatedAt)
        ).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public int importBatch(List<CreateManufacturerRequest> requests) {
        int count = 0;
        for (CreateManufacturerRequest req : requests) {
            try {
                create(req);
                count++;
            } catch (BizException e) {
                log.warn("导入厂家跳过: {} — {}", req.getName(), e.getMessage());
            }
        }
        return count;
    }

    private ManufacturerVO toVO(Manufacturer entity) {
        ManufacturerVO vo = new ManufacturerVO();
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
