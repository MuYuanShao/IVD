package com.zlth.ivd.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlth.ivd.common.utils.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityResponseTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext()); context.register(TestConfiguration.class); context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
    }
    @AfterEach void close() { context.close(); }
    @Test void missingTokenIs401Json() throws Exception {
        mvc.perform(get("/api/v1/security-probe")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40100));
    }
    @Test void invalidOrExpiredTokenIs401Not403() throws Exception {
        mvc.perform(get("/api/v1/security-probe").header("Authorization","Bearer expired")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40100));
    }
    @Test void authenticatedButForbiddenIs403Json() throws Exception {
        mvc.perform(get("/api/v1/security-probe").header("Authorization","Bearer user")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40300));
    }
    @Test void permittedUserStillReachesTheEndpoint() throws Exception {
        mvc.perform(get("/api/v1/security-probe").header("Authorization","Bearer admin")).andExpect(status().isOk());
    }
    @Configuration @EnableWebMvc @Import(SecurityConfig.class)
    static class TestConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean JwtTokenProvider jwtTokenProvider() {
            // Authentication is mocked independently of the provider's validation API name.
            var provider=mock(JwtTokenProvider.class, invocation -> {
                if (invocation.getMethod().getName().startsWith("validate")) {
                    return java.util.List.of("user", "admin").contains(invocation.getArgument(0));
                }
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            });
            for(String token:new String[]{"user","admin"}) {
                when(provider.getUserId(token)).thenReturn(1L);when(provider.getUsername(token)).thenReturn(token);
            }
            when(provider.getRole("user")).thenReturn("USER");when(provider.getRole("admin")).thenReturn("SYSTEM_ADMIN");return provider;
        }
        @Bean ProbeController probeController() { return new ProbeController(); }
    }
    @RestController public static class ProbeController {
        @GetMapping("/api/v1/security-probe") @PreAuthorize("hasRole('SYSTEM_ADMIN')")
        public String probe() { return "ok"; }
    }
}
