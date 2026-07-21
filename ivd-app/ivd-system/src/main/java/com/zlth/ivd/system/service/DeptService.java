package com.zlth.ivd.system.service;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.system.vo.DeptReagentVO;
import com.zlth.ivd.system.vo.DeptVO;

import java.util.List;

/**
 * 科室管理服务 — M00-2
 *
 * @author IVD Team
 * @since 2026-07-13
 */
public interface DeptService {

    PageVO<DeptVO> list(int page, int pageSize, String keyword);

    DeptVO getById(Long id);

    DeptVO create(String deptName, String deptCode, Long parentId, Integer sortOrder,
                  String contactPerson, String contactPhone, String remark);

    DeptVO update(Long id, String deptName, String deptCode, Long parentId, Integer sortOrder,
                  String contactPerson, String contactPhone, String remark);

    void delete(Long id);

    void toggleStatus(Long id);

    List<DeptVO> tree();

    List<DeptVO> options();

    // ── 科室试剂目录 ──

    /** 查询科室已分配的试剂目录 */
    List<DeptReagentVO> listDeptReagents(Long deptId);

    /** 批量添加试剂到科室目录 */
    void addDeptReagents(Long deptId, List<Long> reagentIds);

    /** 批量移除科室试剂目录 */
    void removeDeptReagents(Long deptId, List<Long> reagentIds);
}
