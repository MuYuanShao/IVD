package com.zlth.ivd.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.inventory.entity.Warehouse;
import com.zlth.ivd.inventory.mapper.WarehouseMapper;
import com.zlth.ivd.system.entity.SysDept;
import com.zlth.ivd.system.mapper.SysDeptMapper;
import com.zlth.ivd.system.service.WarehouseService;
import com.zlth.ivd.system.vo.WarehouseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseMapper warehouseMapper;
    private final SysDeptMapper deptMapper;

    private static final Map<Integer, String> LEVEL_LABELS = Map.of(
            1, "中心库", 2, "科室库", 3, "小组库"
    );

    @Override
    public PageVO<WarehouseVO> list(int page, int pageSize, String keyword, Integer level) {
        LambdaQueryWrapper<Warehouse> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Warehouse::getName, keyword).or().like(Warehouse::getCode, keyword));
        }
        if (level != null) qw.eq(Warehouse::getLevel, level);
        qw.orderByAsc(Warehouse::getLevel, Warehouse::getSortOrder);

        Page<Warehouse> p = warehouseMapper.selectPage(new Page<>(page, Math.min(pageSize, 200)), qw);

        // batch-fetch parent names & dept names
        Set<Long> parentIds = p.getRecords().stream().map(Warehouse::getParentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> deptIds = p.getRecords().stream().map(Warehouse::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> whNameMap = new HashMap<>();
        Map<Long, String> deptNameMap = new HashMap<>();
        if (!parentIds.isEmpty()) {
            warehouseMapper.selectBatchIds(parentIds).forEach(w -> whNameMap.put(w.getId(), w.getName()));
        }
        if (!deptIds.isEmpty()) {
            deptMapper.selectBatchIds(deptIds).forEach(d -> deptNameMap.put(d.getId(), d.getDeptName()));
        }

        List<WarehouseVO> records = p.getRecords().stream().map(w -> toVO(w, whNameMap, deptNameMap)).collect(Collectors.toList());
        return new PageVO<>(p.getTotal(), page, pageSize, records);
    }

    @Override
    public WarehouseVO getById(Long id) {
        Warehouse w = warehouseMapper.selectById(id);
        if (w == null) throw new BizException(40400, "库房不存在: id=" + id);
        return toVO(w, getWhNameMap(w.getParentId()), getDeptNameMap(w.getDeptId()));
    }

    @Override
    public WarehouseVO create(String name, String code, Integer level, Long parentId, Long deptId,
                              String location, String contactPerson, String contactPhone, Integer sortOrder) {
        // uniqueness check
        if (StringUtils.hasText(code)) {
            Long count = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getCode, code));
            if (count != null && count > 0) throw new BizException(40001, "库房编码已存在: " + code);
        }
        // level validation
        if (level == null) throw new BizException(40001, "库房级别不能为空");
        validateLevel(level, parentId);

        Warehouse w = new Warehouse();
        w.setName(name);
        w.setCode(code);
        w.setLevel(level);
        w.setParentId(parentId);
        w.setDeptId(deptId);
        w.setLocation(location);
        w.setContactPerson(contactPerson);
        w.setContactPhone(contactPhone);
        w.setSortOrder(sortOrder != null ? sortOrder : 99);
        w.setStatus(1);
        w.setIsActive(true);
        w.setCreatedAt(LocalDateTime.now());
        w.setUpdatedAt(LocalDateTime.now());
        warehouseMapper.insert(w);

        return toVO(w, getWhNameMap(parentId), getDeptNameMap(deptId));
    }

    @Override
    public WarehouseVO update(Long id, String name, String code, Integer level, Long parentId, Long deptId,
                              String location, String contactPerson, String contactPhone, Integer sortOrder) {
        Warehouse w = warehouseMapper.selectById(id);
        if (w == null) throw new BizException(40400, "库房不存在: id=" + id);

        // uniqueness check if code changed
        if (StringUtils.hasText(code) && !code.equals(w.getCode())) {
            Long count = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getCode, code));
            if (count != null && count > 0) throw new BizException(40001, "库房编码已存在: " + code);
        }
        if (level != null) validateLevel(level, parentId != null ? parentId : w.getParentId());

        if (StringUtils.hasText(name)) w.setName(name);
        if (code != null) w.setCode(code);
        if (level != null) w.setLevel(level);
        if (parentId != null) w.setParentId(parentId);
        if (deptId != null) w.setDeptId(deptId);
        if (location != null) w.setLocation(location);
        if (contactPerson != null) w.setContactPerson(contactPerson);
        if (contactPhone != null) w.setContactPhone(contactPhone);
        if (sortOrder != null) w.setSortOrder(sortOrder);
        w.setUpdatedAt(LocalDateTime.now());
        warehouseMapper.updateById(w);

        return toVO(w, getWhNameMap(w.getParentId()), getDeptNameMap(w.getDeptId()));
    }

    @Override
    public void delete(Long id) {
        Warehouse w = warehouseMapper.selectById(id);
        if (w == null) throw new BizException(40400, "库房不存在: id=" + id);
        // check child warehouses
        Long childCount = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getParentId, id));
        if (childCount != null && childCount > 0) throw new BizException(40001, "该库房下仍有子库房，请先删除子库房");
        warehouseMapper.deleteById(id);
    }

    @Override
    public void toggleStatus(Long id) {
        Warehouse w = warehouseMapper.selectById(id);
        if (w == null) throw new BizException(40400, "库房不存在: id=" + id);
        int newStatus = (w.getStatus() != null && w.getStatus() == 1) ? 0 : 1;
        // 停用时校验
        if (newStatus == 0) {
            Long childCount = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getParentId, id));
            if (childCount != null && childCount > 0) throw new BizException(40001, "该库房下仍有子库房启用中，请先停用子库房");
            // TODO: check stock > 0 via inventory service
        }
        w.setStatus(newStatus);
        w.setIsActive(newStatus == 1);
        w.setUpdatedAt(LocalDateTime.now());
        warehouseMapper.updateById(w);
    }

    @Override
    public List<WarehouseVO> tree() {
        List<Warehouse> all = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getStatus, 1).orderByAsc(Warehouse::getSortOrder));
        Map<Long, String> whNames = all.stream().collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));

        Set<Long> deptIds = all.stream().map(Warehouse::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNameMap = new HashMap<>();
        if (!deptIds.isEmpty()) {
            deptMapper.selectBatchIds(deptIds).forEach(d -> deptNameMap.put(d.getId(), d.getDeptName()));
        }

        return all.stream().map(w -> toVO(w, whNames, deptNameMap)).collect(Collectors.toList());
    }

    @Override
    public List<WarehouseVO> options(Integer level) {
        LambdaQueryWrapper<Warehouse> qw = new LambdaQueryWrapper<>();
        if (level != null) qw.eq(Warehouse::getLevel, level);
        qw.eq(Warehouse::getStatus, 1).orderByAsc(Warehouse::getSortOrder);
        return warehouseMapper.selectList(qw).stream().map(w -> {
            WarehouseVO vo = new WarehouseVO();
            vo.setId(w.getId());
            vo.setName(w.getName());
            vo.setCode(w.getCode());
            vo.setLevel(w.getLevel());
            vo.setParentId(w.getParentId());
            vo.setDeptId(w.getDeptId());
            return vo;
        }).collect(Collectors.toList());
    }

    /** 三级库房层级规则: center(1) → dept(2) → group(3) */
    private void validateLevel(Integer level, Long parentId) {
        if (level == 1 && parentId != null)
            throw new BizException(40001, "中心库不能有上级库房");
        if (level > 1 && parentId == null)
            throw new BizException(40001, "非中心库必须指定上级库房");
        if (parentId != null) {
            Warehouse parent = warehouseMapper.selectById(parentId);
            if (parent == null) throw new BizException(40400, "上级库房不存在: id=" + parentId);
            if (parent.getLevel() != level - 1)
                throw new BizException(40001, String.format("库房层级不合法: level=%d 的上级必须是 level=%d, 但 parent level=%d",
                        level, level - 1, parent.getLevel()));
        }
    }

    private Map<Long, String> getWhNameMap(Long parentId) {
        if (parentId == null) return Collections.emptyMap();
        Warehouse parent = warehouseMapper.selectById(parentId);
        return parent != null ? Map.of(parentId, parent.getName()) : Collections.emptyMap();
    }

    private Map<Long, String> getDeptNameMap(Long deptId) {
        if (deptId == null) return Collections.emptyMap();
        SysDept dept = deptMapper.selectById(deptId);
        return dept != null ? Map.of(deptId, dept.getDeptName()) : Collections.emptyMap();
    }

    private WarehouseVO toVO(Warehouse w, Map<Long, String> whNameMap, Map<Long, String> deptNameMap) {
        WarehouseVO vo = new WarehouseVO();
        vo.setId(w.getId());
        vo.setName(w.getName());
        vo.setCode(w.getCode());
        vo.setLevel(w.getLevel());
        vo.setLevelLabel(LEVEL_LABELS.getOrDefault(w.getLevel(), "—"));
        vo.setParentId(w.getParentId());
        vo.setParentName(w.getParentId() != null ? whNameMap.getOrDefault(w.getParentId(), "—") : "—");
        vo.setDeptId(w.getDeptId());
        vo.setDeptName(w.getDeptId() != null ? deptNameMap.getOrDefault(w.getDeptId(), "—") : "—");
        vo.setLocation(w.getLocation());
        vo.setContactPerson(w.getContactPerson());
        vo.setContactPhone(w.getContactPhone());
        vo.setSortOrder(w.getSortOrder());
        vo.setStatus(w.getStatus());
        vo.setRemark(w.getRemark());
        vo.setCreatedAt(w.getCreatedAt());
        vo.setUpdatedAt(w.getUpdatedAt());
        return vo;
    }
}
