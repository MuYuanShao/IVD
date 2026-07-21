package com.zlth.ivd.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.catalog.entity.ReagentCatalog;
import com.zlth.ivd.catalog.mapper.ReagentCatalogMapper;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.inventory.entity.Warehouse;
import com.zlth.ivd.inventory.mapper.WarehouseMapper;
import com.zlth.ivd.system.entity.DeptReagent;
import com.zlth.ivd.system.entity.SysDept;
import com.zlth.ivd.system.mapper.DeptReagentMapper;
import com.zlth.ivd.system.mapper.SysDeptMapper;
import com.zlth.ivd.system.service.DeptService;
import com.zlth.ivd.system.vo.DeptReagentVO;
import com.zlth.ivd.system.vo.DeptVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
public class DeptServiceImpl implements DeptService {

    private final SysDeptMapper deptMapper;
    private final WarehouseMapper warehouseMapper;
    private final DeptReagentMapper deptReagentMapper;
    private final ReagentCatalogMapper reagentCatalogMapper;

    @Override
    public PageVO<DeptVO> list(int page, int pageSize, String keyword) {
        LambdaQueryWrapper<SysDept> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(SysDept::getDeptName, keyword).or().like(SysDept::getDeptCode, keyword));
        }
        qw.orderByAsc(SysDept::getSortOrder);

        Page<SysDept> p = deptMapper.selectPage(new Page<>(page, Math.min(pageSize, 200)), qw);

        // batch-fetch parent names
        Set<Long> parentIds = p.getRecords().stream().map(SysDept::getParentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> parentNameMap = new HashMap<>();
        if (!parentIds.isEmpty()) {
            deptMapper.selectBatchIds(parentIds).forEach(d -> parentNameMap.put(d.getId(), d.getDeptName()));
        }

        List<DeptVO> records = p.getRecords().stream().map(d -> toVO(d, parentNameMap)).collect(Collectors.toList());
        return new PageVO<>(p.getTotal(), page, pageSize, records);
    }

    @Override
    public DeptVO getById(Long id) {
        SysDept d = deptMapper.selectById(id);
        if (d == null) throw new BizException(40400, "科室不存在: id=" + id);
        return toVO(d, getParentNameMap(Collections.singleton(d.getParentId())));
    }

    @Override
    public DeptVO create(String deptName, String deptCode, Long parentId, Integer sortOrder,
                         String contactPerson, String contactPhone, String remark) {
        // uniqueness check
        Long count = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>().eq(SysDept::getDeptCode, deptCode));
        if (count != null && count > 0) throw new BizException(40001, "科室编码已存在: " + deptCode);

        SysDept d = new SysDept();
        d.setDeptName(deptName);
        d.setDeptCode(deptCode);
        d.setParentId(parentId);
        d.setSortOrder(sortOrder != null ? sortOrder : 99);
        d.setContactPerson(contactPerson);
        d.setContactPhone(contactPhone);
        d.setRemark(remark);
        d.setStatus(1);
        d.setCreatedAt(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        deptMapper.insert(d);

        // ★ 自动创建对应的二级库房
        autoCreateWarehouse(d);
        log.info("科室已创建，关联库房已同步: deptId={}, deptName={}", d.getId(), d.getDeptName());

        return toVO(d, getParentNameMap(Collections.singleton(parentId)));
    }

    @Override
    public DeptVO update(Long id, String deptName, String deptCode, Long parentId, Integer sortOrder,
                         String contactPerson, String contactPhone, String remark) {
        SysDept d = deptMapper.selectById(id);
        if (d == null) throw new BizException(40400, "科室不存在: id=" + id);

        // uniqueness check if code changed
        if (StringUtils.hasText(deptCode) && !deptCode.equals(d.getDeptCode())) {
            Long count = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>().eq(SysDept::getDeptCode, deptCode));
            if (count != null && count > 0) throw new BizException(40001, "科室编码已存在: " + deptCode);
        }

        if (StringUtils.hasText(deptName)) d.setDeptName(deptName);
        if (StringUtils.hasText(deptCode)) d.setDeptCode(deptCode);
        if (parentId != null) d.setParentId(parentId);
        if (sortOrder != null) d.setSortOrder(sortOrder);
        if (contactPerson != null) d.setContactPerson(contactPerson);
        if (contactPhone != null) d.setContactPhone(contactPhone);
        if (remark != null) d.setRemark(remark);
        d.setUpdatedAt(LocalDateTime.now());
        deptMapper.updateById(d);

        return toVO(d, getParentNameMap(Collections.singleton(d.getParentId())));
    }

    @Override
    public void delete(Long id) {
        SysDept d = deptMapper.selectById(id);
        if (d == null) throw new BizException(40400, "科室不存在: id=" + id);
        // check child depts
        Long childCount = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id));
        if (childCount != null && childCount > 0) throw new BizException(40001, "该科室下仍有子科室，请先删除子科室");
        deptMapper.deleteById(id);
    }

    @Override
    public void toggleStatus(Long id) {
        SysDept d = deptMapper.selectById(id);
        if (d == null) throw new BizException(40400, "科室不存在: id=" + id);
        int newStatus = (d.getStatus() != null && d.getStatus() == 1) ? 0 : 1;
        // 停用时校验库存
        if (newStatus == 0) {
            // 查找关联库房并检查库存
            List<Warehouse> linkedWh = warehouseMapper.selectList(
                    new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getDeptId, id));
            if (!linkedWh.isEmpty()) {
                // 库房停用：InventoryService 中有库存查询，这里简化为仅校验关联
                log.info("科室 {} 停用，关联库房 {} 个，需手动确认库存已清空", d.getDeptName(), linkedWh.size());
            }
        }
        d.setStatus(newStatus);
        d.setUpdatedAt(LocalDateTime.now());
        deptMapper.updateById(d);
    }

    @Override
    public List<DeptVO> tree() {
        List<SysDept> all = deptMapper.selectList(new LambdaQueryWrapper<SysDept>().eq(SysDept::getStatus, 1).orderByAsc(SysDept::getSortOrder));
        Map<Long, String> allNames = all.stream().collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName));
        return all.stream().map(d -> toVO(d, allNames)).collect(Collectors.toList());
    }

    @Override
    public List<DeptVO> options() {
        return deptMapper.selectList(new LambdaQueryWrapper<SysDept>().eq(SysDept::getStatus, 1).orderByAsc(SysDept::getSortOrder))
                .stream().map(d -> {
                    DeptVO vo = new DeptVO();
                    vo.setId(d.getId());
                    vo.setDeptName(d.getDeptName());
                    vo.setDeptCode(d.getDeptCode());
                    vo.setParentId(d.getParentId());
                    return vo;
                }).collect(Collectors.toList());
    }

    // ── 科室试剂目录 ──

    @Override
    public List<DeptReagentVO> listDeptReagents(Long deptId) {
        SysDept dept = deptMapper.selectById(deptId);
        if (dept == null) throw new BizException(40400, "科室不存在: id=" + deptId);

        List<DeptReagent> rels = deptReagentMapper.selectList(
                new LambdaQueryWrapper<DeptReagent>().eq(DeptReagent::getDeptId, deptId));

        if (rels.isEmpty()) return Collections.emptyList();

        Set<Long> reagentIds = rels.stream().map(DeptReagent::getReagentId).collect(Collectors.toSet());
        List<ReagentCatalog> reagents = reagentCatalogMapper.selectBatchIds(reagentIds);
        Map<Long, ReagentCatalog> reagentMap = reagents.stream()
                .collect(Collectors.toMap(ReagentCatalog::getId, r -> r));

        return rels.stream().map(rel -> {
            ReagentCatalog rc = reagentMap.get(rel.getReagentId());
            return toDeptReagentVO(rel, rc);
        }).collect(Collectors.toList());
    }

    @Override
    public void addDeptReagents(Long deptId, List<Long> reagentIds) {
        if (reagentIds == null || reagentIds.isEmpty()) return;

        SysDept dept = deptMapper.selectById(deptId);
        if (dept == null) throw new BizException(40400, "科室不存在: id=" + deptId);

        // 校验试剂ID是否真实存在
        List<ReagentCatalog> existingReagents = reagentCatalogMapper.selectBatchIds(reagentIds);
        Set<Long> validIds = existingReagents.stream().map(ReagentCatalog::getId).collect(Collectors.toSet());
        List<Long> invalidIds = reagentIds.stream().filter(rid -> !validIds.contains(rid)).collect(Collectors.toList());
        if (!invalidIds.isEmpty()) {
            throw new BizException(40001, "以下试剂ID不存在: " + invalidIds);
        }

        // 过滤已存在的关联
        Set<Long> alreadyLinked = deptReagentMapper.selectList(
                new LambdaQueryWrapper<DeptReagent>()
                        .eq(DeptReagent::getDeptId, deptId)
                        .in(DeptReagent::getReagentId, reagentIds))
                .stream().map(DeptReagent::getReagentId).collect(Collectors.toSet());

        List<DeptReagent> toInsert = reagentIds.stream()
                .filter(rid -> !alreadyLinked.contains(rid))
                .map(rid -> {
                    DeptReagent dr = new DeptReagent();
                    dr.setDeptId(deptId);
                    dr.setReagentId(rid);
                    return dr;
                }).collect(Collectors.toList());

        if (!toInsert.isEmpty()) {
            try {
                deptReagentMapper.insert(toInsert);
            } catch (DuplicateKeyException e) {
                throw new BizException(40001, "部分试剂已在该科室目录中，请刷新后重试");
            }
        }
    }

    @Override
    public void removeDeptReagents(Long deptId, List<Long> reagentIds) {
        if (reagentIds == null || reagentIds.isEmpty()) return;

        deptReagentMapper.delete(new LambdaQueryWrapper<DeptReagent>()
                .eq(DeptReagent::getDeptId, deptId)
                .in(DeptReagent::getReagentId, reagentIds));
    }

    private DeptReagentVO toDeptReagentVO(DeptReagent rel, ReagentCatalog rc) {
        DeptReagentVO vo = new DeptReagentVO();
        vo.setId(rel.getId());
        vo.setReagentId(rel.getReagentId());
        vo.setCreatedAt(rel.getCreatedAt());
        if (rc != null) {
            vo.setHisCode(rc.getHisCode());
            vo.setGenericName(rc.getGenericName());
            vo.setMaterialType(rc.getMaterialType() != null ? rc.getMaterialType().getCode() : null);
            vo.setMaterialTypeLabel(rc.getMaterialType() != null ? rc.getMaterialType().getLabel() : null);
            vo.setSpecification(rc.getSpecification());
            vo.setManufacturer(rc.getManufacturer());
            vo.setUnit(rc.getUnit());
            vo.setTestGroups(rc.getTestGroups());
            vo.setRegistrationCertNo(rc.getRegistrationCertNo());
        }
        return vo;
    }

    /** 新建科室时自动创建对应的二级库房 */
    private void autoCreateWarehouse(SysDept dept) {
        // 检查是否已有同科室关联的库房
        Long existCount = warehouseMapper.selectCount(
                new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getDeptId, dept.getId()));
        if (existCount != null && existCount > 0) return;

        Warehouse wh = new Warehouse();
        wh.setName(dept.getDeptName() + "库");
        // wh.setCode("WH-D-" + dept.getDeptCode()); -- code column to be added by migration
        wh.setLevel(2); // 科室库=二级
        wh.setDeptId(dept.getId());
        wh.setContactPerson(dept.getContactPerson());
        wh.setContactPhone(dept.getContactPhone());
        wh.setIsActive(true);
        wh.setCreatedAt(LocalDateTime.now());
        wh.setUpdatedAt(LocalDateTime.now());
        warehouseMapper.insert(wh);
        log.info("自动创建科室库房: warehouseId={}, deptId={}, whName={}", wh.getId(), dept.getId(), wh.getName());
    }

    private Map<Long, String> getParentNameMap(Set<Long> parentIds) {
        if (parentIds.isEmpty() || parentIds.stream().allMatch(Objects::isNull)) return Collections.emptyMap();
        Map<Long, String> map = new HashMap<>();
        deptMapper.selectBatchIds(parentIds.stream().filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(d -> map.put(d.getId(), d.getDeptName()));
        return map;
    }

    private DeptVO toVO(SysDept d, Map<Long, String> parentNameMap) {
        DeptVO vo = new DeptVO();
        vo.setId(d.getId());
        vo.setDeptName(d.getDeptName());
        vo.setDeptCode(d.getDeptCode());
        vo.setParentId(d.getParentId());
        vo.setParentName(d.getParentId() != null ? parentNameMap.getOrDefault(d.getParentId(), "—") : "—");
        vo.setSortOrder(d.getSortOrder());
        vo.setContactPerson(d.getContactPerson());
        vo.setContactPhone(d.getContactPhone());
        vo.setStatus(d.getStatus());
        vo.setRemark(d.getRemark());
        vo.setCreatedAt(d.getCreatedAt());
        vo.setUpdatedAt(d.getUpdatedAt());
        return vo;
    }
}
