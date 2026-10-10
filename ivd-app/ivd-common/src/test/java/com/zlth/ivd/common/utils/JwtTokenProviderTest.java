package com.zlth.ivd.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtTokenProviderTest {

    @Test
    void scopedRefreshMustRejectLegacyForeignAndAccessTokens() {
        String token = provider.generateRefreshToken(7L, 2L);
        org.junit.jupiter.api.Assertions.assertTrue(provider.validateInstitutionRefreshToken(token, 2L));
        org.junit.jupiter.api.Assertions.assertFalse(provider.validateInstitutionRefreshToken(token, 3L));
        org.junit.jupiter.api.Assertions.assertFalse(provider.validateInstitutionRefreshToken(provider.generateRefreshToken(7L), 2L));
        org.junit.jupiter.api.Assertions.assertFalse(provider.validateInstitutionRefreshToken(provider.generateToken(7L, "user", "LAB_TECH", 2L, 5L), 2L));
        org.junit.jupiter.api.Assertions.assertFalse(provider.validateInstitutionRefreshToken("invalid", 2L));
    }

    private final JwtTokenProvider provider = new JwtTokenProvider(
            "0123456789abcdef0123456789abcdef", 60_000, 120_000);

    @Test
    void generatedTokensMustCarryTheirType() {
        String accessToken = provider.generateToken(1L, "admin", "SYSTEM_ADMIN");
        String refreshToken = provider.generateRefreshToken(1L);

        assertEquals("access", provider.parseToken(accessToken).get("token_type"));
        assertEquals("refresh", provider.parseToken(refreshToken).get("token_type"));
    }
}
