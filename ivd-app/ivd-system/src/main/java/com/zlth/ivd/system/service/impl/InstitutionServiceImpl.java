package com.zlth.ivd.system.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.system.dto.InstitutionRequest;
import com.zlth.ivd.system.entity.Institution;
import com.zlth.ivd.system.mapper.InstitutionMapper;
import com.zlth.ivd.system.service.*;
import com.zlth.ivd.system.vo.InstitutionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional(rollbackFor=Exception.class)
public class InstitutionServiceImpl implements InstitutionService {
    private final InstitutionMapper mapper;
    private final AuthenticationDirectoryService directory;
    public PageVO<InstitutionVO> list(int page,int size,String keyword) {
        requireAdmin(); var query=new LambdaQueryWrapper<Institution>();
        if(keyword!=null && !keyword.isBlank()) query.and(q->q.like(Institution::getCode,keyword).or().like(Institution::getName,keyword));
        query.orderByAsc(Institution::getId);
        var result=mapper.selectPage(new Page<Institution>(Math.max(1,page),Math.min(200,Math.max(1,size))),query);
        return new PageVO<>(result.getTotal(),page,size,result.getRecords().stream().map(this::vo).toList());
    }
    public List<InstitutionVO> options() {
        var scope=InstitutionContext.require(); var query=new LambdaQueryWrapper<Institution>().eq(Institution::getStatus,1);
        if(!scope.role().equals("SYSTEM_ADMIN")) query.and(q->q.eq(Institution::getId,scope.institutionId()).or().eq(Institution::getOwnerInstitutionId,scope.institutionId()));
        return mapper.selectList(query.orderByAsc(Institution::getId)).stream().map(this::vo).toList();
    }
    public InstitutionVO create(InstitutionRequest request) {
        requireAdmin(); unique(null,request.code()); var entity=new Institution();
        entity.setOwnerInstitutionId(request.type().equals("EXTERNAL")?InstitutionContext.require().institutionId():null);
        copy(entity,request); entity.setCreatedAt(LocalDateTime.now()); mapper.insert(entity); return vo(entity);
    }
    public InstitutionVO update(Long id,InstitutionRequest request) {
        requireAdmin(); var entity=mapper.selectById(id); if(entity==null) throw new BizException(40400,"机构不存在");
        if(!entity.getType().equals(request.type())) throw new BizException(40900,"机构类型不能修改");
        if(entity.getType().equals("EXTERNAL") && !Objects.equals(entity.getOwnerInstitutionId(),InstitutionContext.require().institutionId())) throw new BizException(40400,"机构不存在或无权访问");
        var actor=directory.findActiveUser(InstitutionContext.requireUserActor());
        if(request.status()==0 && Objects.equals(actor.getInstitutionId(),id)) throw new BizException(40900,"不能停用当前账号所属机构");
        unique(id,request.code()); copy(entity,request); mapper.updateById(entity); return vo(entity);
    }
    private void unique(Long id,String code) {
        var query=new LambdaQueryWrapper<Institution>().eq(Institution::getCode,code.trim()); if(id!=null) query.ne(Institution::getId,id);
        if(mapper.selectCount(query)>0) throw new BizException(40900,"机构编码已存在");
    }
    private void copy(Institution value,InstitutionRequest request) {
        value.setCode(request.code().trim()); value.setName(request.name().trim()); value.setType(request.type());
        value.setContactPerson(request.contactPerson()); value.setContactPhone(request.contactPhone()); value.setAddress(request.address());
        value.setCapabilities(request.capabilities()); value.setStatus(request.status()); value.setUpdatedAt(LocalDateTime.now());
    }
    private void requireAdmin(){if(!InstitutionContext.require().role().equals("SYSTEM_ADMIN")) throw new BizException(40300,"仅管理员可维护机构");}
    private InstitutionVO vo(Institution value){return new InstitutionVO(value.getId(),value.getCode(),value.getName(),value.getType(),value.getOwnerInstitutionId(),value.getContactPerson(),value.getContactPhone(),value.getAddress(),value.getCapabilities(),value.getStatus());}
}
