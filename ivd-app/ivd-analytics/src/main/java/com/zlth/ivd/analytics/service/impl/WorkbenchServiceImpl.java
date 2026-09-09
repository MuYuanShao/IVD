package com.zlth.ivd.analytics.service.impl;

import com.zlth.ivd.analytics.mapper.WorkbenchMapper;
import com.zlth.ivd.analytics.service.WorkbenchService;
import com.zlth.ivd.analytics.vo.WorkbenchVO;
import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly=true, rollbackFor=Exception.class, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class WorkbenchServiceImpl implements WorkbenchService {
    private final WorkbenchMapper mapper;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public WorkbenchServiceImpl(WorkbenchMapper mapper) { this(mapper, Clock.system(ZoneId.of("Asia/Shanghai"))); }
    WorkbenchServiceImpl(WorkbenchMapper mapper, Clock clock) { this.mapper=mapper; this.clock=clock; }

    private Long scope(Long userId) {
        if (userId == null) throw new BizException(40100,"请重新登录");
        var user=mapper.userScope(userId);
        if (user == null) throw new BizException(40300,"账号不存在或已停用");
        if ("SYSTEM_ADMIN".equals(user.getRole()) || "ADMIN".equals(user.getRole())) return null;
        if (user.getDeptId()==null) throw new BizException(40300,"未分配科室，无法确定数据范围，请联系管理员");
        return user.getDeptId();
    }

    @Override public WorkbenchVO overview(Long userId) {
        Long dept=scope(userId);
        LocalDate today=LocalDate.now(clock);
        var result=new WorkbenchVO();
        result.setBusinessDate(today); result.setGeneratedAt(LocalDateTime.now(clock));
        result.setTimezone(clock.getZone().getId()); result.setScope(dept==null ? "全院" : "本科室");
        result.setMetrics(mapper.metrics(dept,today.atStartOfDay(),today.plusDays(1).atStartOfDay()));
        var alerts=mapper.alerts(dept,today,today.plusDays(90)); alerts.setShortageOrders(mapper.shortages(dept)); result.setAlerts(alerts);
        result.setTodoTotal(mapper.todoCount(dept)); result.setTodos(mapper.todos(dept));
        Map<LocalDate,WorkbenchVO.Trend> indexed=mapper.trend(dept,today.minusDays(29).atStartOfDay(),today.plusDays(1).atStartOfDay()).stream().collect(Collectors.toMap(WorkbenchVO.Trend::getDate,Function.identity()));
        List<WorkbenchVO.Trend> days=new ArrayList<>();
        for (int i=29;i>=0;i--) {
            LocalDate date=today.minusDays(i);
            var point=indexed.get(date);
            if (point==null) { point=new WorkbenchVO.Trend(); point.setDate(date); point.setInbound(BigDecimal.ZERO); point.setOutbound(BigDecimal.ZERO); }
            days.add(point);
        }
        result.setTrend(days); return result;
    }

    @Override public PageVO<WorkbenchVO.Product> products(Long userId,int page,int pageSize,String keyword) {
        if (page<1 || page>1000000 || pageSize<1 || pageSize>100) throw new BizException(40001,"page须为1至1000000，pageSize须为1至100");
        String search=keyword==null ? "" : keyword.trim();
        if(search.length()>100) throw new BizException(40001,"搜索词不能超过100字符");
        Long dept=scope(userId); long total=mapper.productCount(dept,search);
        long offset=(long)(page-1)*pageSize;
        return new PageVO<>(total,page,pageSize,offset>=total ? List.of() : mapper.products(dept,search,offset,pageSize));
    }
}
