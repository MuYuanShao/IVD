package com.zlth.ivd.inventory.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.inventory.mapper.RemainingBatchMapper;
import com.zlth.ivd.inventory.service.RemainingBatchService;
import com.zlth.ivd.inventory.vo.RemainingBatchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true, rollbackFor=Exception.class)
public class RemainingBatchServiceImpl implements RemainingBatchService {
    private final RemainingBatchMapper mapper;

    @Override public PageVO<RemainingBatchVO> list(int page, int pageSize, String keyword, Integer level) {
        InstitutionContext.requireUserActor();
        var scope=InstitutionContext.require();
        Long departmentId=java.util.Set.of("SYSTEM_ADMIN","STOREKEEPER","FINANCE").contains(scope.role())?null:scope.departmentId();
        if(!java.util.Set.of("SYSTEM_ADMIN","STOREKEEPER","FINANCE").contains(scope.role()) && departmentId==null)
            throw new BizException(40300,"请先配置所属科室");
        if(page<1 || pageSize<1 || pageSize>100 || (level!=null && (level<1 || level>3)))
            throw new BizException(40001,"分页或库房层级参数无效");
        if(keyword!=null && keyword.length()>100) throw new BizException(40001,"搜索内容过长");
        var result=mapper.list(new Page<>(page,pageSize),keyword==null?null:keyword.trim(),level,departmentId);
        return new PageVO<>(result.getTotal(),page,pageSize,result.getRecords());
    }
}
