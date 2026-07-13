package com.zlth.ivd.server.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Token 刷新请求
 *
 * @author IVD Team
 * @since 2026-07-06
 */
@Data
public class RefreshRequest {

    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
