package com.zlth.ivd.common.aspect;

import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleAspectTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void classLevelRequireRoleMustBeEnforced() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, "token",
                        List.of(new SimpleGrantedAuthority("ROLE_LAB_TECH"))));

        AspectJProxyFactory factory = new AspectJProxyFactory(new AdminOnlyService());
        factory.addAspect(new RoleAspect());
        AdminOnlyService proxy = factory.getProxy();

        assertThrows(BizException.class, proxy::adminOperation);
    }

    @RequireRole("SYSTEM_ADMIN")
    static class AdminOnlyService {
        public void adminOperation() {
        }
    }
}
