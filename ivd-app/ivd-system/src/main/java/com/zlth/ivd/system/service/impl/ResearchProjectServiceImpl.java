package com.zlth.ivd.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.system.dto.ResearchProjectRequest;
import com.zlth.ivd.system.entity.ResearchProject;
import com.zlth.ivd.system.mapper.ResearchProjectMapper;
import com.zlth.ivd.system.service.ResearchProjectService;
import com.zlth.ivd.system.vo.ResearchProjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor=Exception.class)
public class ResearchProjectServiceImpl implements ResearchProjectService {
    private final ResearchProjectMapper mapper;

    @Override @Transactional(readOnly=true)
    public List<ResearchProjectVO> list(boolean includeDisabled) {
        return mapper.selectList(new LambdaQueryWrapper<ResearchProject>()
                .eq(!includeDisabled, ResearchProject::getEnabled, true)
                .orderByAsc(ResearchProject::getSortOrder, ResearchProject::getId))
                .stream().map(this::toVO).toList();
    }

    @Override public ResearchProjectVO create(ResearchProjectRequest request) {
        ResearchProject project=new ResearchProject();
        copy(request,project); project.setCreatedAt(LocalDateTime.now());
        try { mapper.insert(project); } catch(DuplicateKeyException e) { throw new BizException(40900,"科研项目名称已存在"); }
        return toVO(project);
    }

    @Override public ResearchProjectVO update(Long id, ResearchProjectRequest request) {
        ResearchProject project=mapper.lockById(id);
        if(project==null) throw new BizException(40400,"科研项目不存在");
        copy(request,project);
        try { mapper.updateById(project); } catch(DuplicateKeyException e) { throw new BizException(40900,"科研项目名称已存在"); }
        return toVO(project);
    }

    @Override @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public String requireActiveName(Long id) {
        if(id==null) return null;
        ResearchProject project=mapper.lockById(id);
        if(project==null || !Boolean.TRUE.equals(project.getEnabled())) throw new BizException(40001,"科研项目不存在或已停用，请重新选择");
        return project.getName();
    }

    private void copy(ResearchProjectRequest request,ResearchProject project) {
        if(request==null || request.getEnabled()==null || request.getSortOrder()==null || request.getSortOrder()<0 || request.getSortOrder()>9999) throw new BizException(40001,"项目状态及排序参数不正确");
        if((request.getLeader()!=null && request.getLeader().length()>80) || (request.getRemark()!=null && request.getRemark().length()>500)) throw new BizException(40001,"负责人或备注长度超出限制");
        if(request.getName()==null || request.getName().isBlank() || request.getName().trim().length()>120) throw new BizException(40001,"项目名称须为1至120字符");
        project.setName(request.getName().trim());
        project.setLeader(request.getLeader()==null ? null : request.getLeader().trim());
        project.setRemark(request.getRemark()==null ? null : request.getRemark().trim());
        project.setSortOrder(request.getSortOrder());project.setEnabled(request.getEnabled());project.setUpdatedAt(LocalDateTime.now());
    }

    private ResearchProjectVO toVO(ResearchProject p) {
        return new ResearchProjectVO(p.getId().toString(),p.getName(),p.getLeader(),p.getRemark(),p.getSortOrder(),p.getEnabled());
    }
}
