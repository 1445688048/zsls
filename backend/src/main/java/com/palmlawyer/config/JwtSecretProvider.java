// JwtSecretProvider.java
// JWT 密钥统一提供者：环境变量未注入时生成随进程的随机密钥，
// 避免在配置文件中保存任何固定密钥（固定默认值等于公开密钥）。
package com.palmlawyer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Component
public class JwtSecretProvider {

    private static final int MIN_KEY_BYTES = 32;

    private final String secret;

    public JwtSecretProvider(@Value("${palmlawyer.security.jwt.secret:}") String configured) {
        if (configured != null && !configured.isBlank()) {
            if (configured.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_BYTES) {
                throw new IllegalStateException(
                        "PALMLAWYER_JWT_SECRET 长度不足：HS256 要求密钥至少 " + MIN_KEY_BYTES + " 字节");
            }
            this.secret = configured;
            return;
        }
        // 未配置：生成随机密钥。后果是重启后所有已签发 token 失效（用户需重新登录），
        // 对无状态的开发环境可接受；生产环境必须显式注入固定密钥。
        byte[] raw = new byte[48];
        new SecureRandom().nextBytes(raw);
        this.secret = Base64.getEncoder().encodeToString(raw);
        log.warn("未配置 PALMLAWYER_JWT_SECRET，已生成本次进程的随机 JWT 密钥：重启后所有 token 将失效，生产环境请通过环境变量注入固定密钥");
    }

    public String get() {
        return secret;
    }
}
