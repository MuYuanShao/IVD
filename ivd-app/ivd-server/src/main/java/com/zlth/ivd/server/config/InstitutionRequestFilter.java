package com.zlth.ivd.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.exception.BizException;
import com.zlth.ivd.common.security.InstitutionContext;
import com.zlth.ivd.common.utils.JwtTokenProvider;
import com.zlth.ivd.common.audit.OperationAuditEntry;
import com.zlth.ivd.common.audit.OperationAuditLogWriter;
import com.zlth.ivd.system.service.AuthenticationDirectoryService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;

/** Resolves ownership from current account records, never from request bodies or unverified JWT claims. */
public final class InstitutionRequestFilter extends OncePerRequestFilter {
    private final AuthenticationDirectoryService directory;
    private final JwtTokenProvider tokens;
    private final ObjectMapper json;
    private final OperationAuditLogWriter audits;
    private final boolean enabled;
    public InstitutionRequestFilter(AuthenticationDirectoryService directory,JwtTokenProvider tokens,ObjectMapper json,OperationAuditLogWriter audits,boolean enabled) {
        this.directory=directory; this.tokens=tokens; this.json=json; this.audits=audits; this.enabled=enabled;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/")
                || request.getRequestURI().equals("/api/v1/auth/login") || request.getRequestURI().equals("/api/v1/auth/refresh");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || !(authentication.getPrincipal() instanceof Long userId)) { write(response,401,40100,"请先登录"); return; }
        InstitutionContext.Scope scope;
        try {
            var user=directory.findActiveUser(userId);
            if(user==null) throw new BizException(40100,"账号已停用，请重新登录");
            if(user.getInstitutionId()==null || directory.activeInstitution(user.getInstitutionId())==null)
                throw new BizException(40300,"账号所属机构未配置或已停用");
            String header=request.getHeader("Authorization");
            String token=header==null?"":header.replaceFirst("^Bearer ","");
            if(!Objects.equals(tokens.getInstitutionId(token),user.getInstitutionId()) || !Objects.equals(tokens.getRole(token),user.getRole()))
                throw new BizException(40100,"登录归属或权限已变更，请重新登录");
            // Fixed administrator policy must use the current directory identity, not a stale JWT username.
            if(authentication instanceof org.springframework.security.authentication.AbstractAuthenticationToken verified) {
                verified.setDetails(user.getUsername());
            }
            Long selected=user.getInstitutionId();
            String selectedHeader=request.getHeader("X-Institution-Id");
            if(selectedHeader!=null && !selectedHeader.isBlank()) {
                long requested;
                try { requested=Long.parseLong(selectedHeader); } catch(NumberFormatException error) { throw new BizException(40001,"机构编号无效"); }
                if(!Objects.equals(selected,requested) && !"SYSTEM_ADMIN".equals(user.getRole())) throw new BizException(40300,"不能切换其他机构");
                selected=requested;
            }
            if(selected==null || directory.activeInstitution(selected)==null) throw new BizException(40300,"机构未配置或已停用");
            Long department=Objects.equals(selected,user.getInstitutionId())?user.getDeptId():null;
            if(department!=null) {
                var dept=directory.activeDepartment(department);
                if(dept==null || !(dept.get("institution_id") instanceof Number owner) || owner.longValue()!=selected)
                    throw new BizException(40300,"用户科室与机构归属不一致");
            }
            scope=new InstitutionContext.Scope(selected,department,userId,user.getRole());
            if(!Objects.equals(selected,user.getInstitutionId())) {
                Long target=selected;
                InstitutionContext.within(scope,()->{
                    audits.append(OperationAuditEntry.builder().operatorId(userId).operatorName(user.getUsername())
                            .module("institution").action("SELECT_INSTITUTION").handler("InstitutionRequestFilter")
                            .reason("Selected institution="+target).requestMethod(request.getMethod()).requestPath(request.getRequestURI())
                            .success(true).durationMs(0L).occurredAt(LocalDateTime.now()).build());
                    return null;
                });
            }
        } catch(BizException error) { write(response,error.getErrorCode()==40100?401:error.getErrorCode()==40001?400:403,error.getErrorCode(),error.getMessage()); return; }
          catch(io.jsonwebtoken.JwtException|IllegalArgumentException error) { write(response,401,40100,"登录已失效，请重新登录"); return; }
        try {
            InstitutionContext.within(scope,()->{
                try { chain.doFilter(request,response); } catch(IOException|ServletException error) { throw new FilterFailure(error); }
                return null;
            });
        } catch(FilterFailure error) {
            if(error.getCause() instanceof IOException io) throw io;
            throw (ServletException)error.getCause();
        }
    }
    private void write(HttpServletResponse response,int status,int code,String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(),Result.error(code,message));
    }
    private static final class FilterFailure extends RuntimeException { private FilterFailure(Exception cause){super(cause);} }
}
