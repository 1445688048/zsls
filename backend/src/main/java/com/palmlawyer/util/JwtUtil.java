// JwtUtil.java
// 轻量 JWT(HS256) 工具：纯 JDK 实现，无第三方依赖
// 格式: base64url(header).base64url(payload).base64url(hmac-sha256(签名输入))
package com.palmlawyer.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JwtUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private JwtUtil() {}

    /**
     * 签发 HS256 JWT
     *
     * @param userId     用户 ID
     * @param secret     密钥（必须 >= 32 字节）
     * @param ttlMillis  有效期毫秒
     */
    public static String issue(Long userId, String secret, long ttlMillis) {
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");
            long now = System.currentTimeMillis();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", String.valueOf(userId));
            payload.put("iat", now / 1000);
            payload.put("exp", (now + ttlMillis) / 1000);
            String head = B64.encodeToString(MAPPER.writeValueAsBytes(header));
            String body = B64.encodeToString(MAPPER.writeValueAsBytes(payload));
            String signingInput = head + "." + body;
            String sig = B64.encodeToString(hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8), secret));
            return signingInput + "." + sig;
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签发失败", e);
        }
    }

    /**
     * 校验并解析 JWT，返回 userId；无效或过期抛 IllegalArgumentException
     */
    public static Long verify(String token, String secret) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException("token 格式错误");
            String signingInput = parts[0] + "." + parts[1];
            byte[] expect = hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8), secret);
            byte[] actual = B64D.decode(parts[2]);
            if (!MessageDigest.isEqual(expect, actual)) throw new IllegalArgumentException("签名校验失败");
            Map<?, ?> payload = MAPPER.readValue(B64D.decode(parts[1]), Map.class);
            Object exp = payload.get("exp");
            long expSec = exp instanceof Number ? ((Number) exp).longValue() : Long.parseLong(String.valueOf(exp));
            if (System.currentTimeMillis() / 1000 >= expSec) throw new IllegalArgumentException("token 已过期");
            return Long.valueOf(String.valueOf(payload.get("sub")));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("token 解析失败", e);
        }
    }

    private static byte[] hmacSha256(byte[] data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data);
    }
}
