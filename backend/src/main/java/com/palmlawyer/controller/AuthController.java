// AuthController.java
// 认证控制器：微信 code2session 换 openid -> 签发 JWT
package com.palmlawyer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.palmlawyer.entity.AppUser;
import com.palmlawyer.mapper.AppUserMapper;
import com.palmlawyer.util.AuthContext;
import com.palmlawyer.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 *
 * <p>处理微信小程序登录流程：
 * <ol>
 *   <li>前端调用 wx.login() 获取 code</li>
 *   <li>后端用 code 调 jscode2session 换取 openid</li>
 *   <li>签发 JWT 返回给前端</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AppUserMapper userMapper;
    private final RestClient restClient = RestClient.create();

    public AuthController(AppUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Value("${palmlawyer.wechat.app-id}")
    private String appId;

    @Value("${palmlawyer.wechat.app-secret}")
    private String appSecret;

    @Value("${palmlawyer.auth.dev-mode}")
    private boolean devMode;

    @Value("${palmlawyer.security.jwt.secret}")
    private String jwtSecret;

    @Value("${palmlawyer.security.jwt.expiration}")
    private String jwtExpiration;

    /**
     * 微信登录接口
     *
     * @param body 包含 code 的请求体
     * @return token、userId、openid
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 code");
        }

        String openid = exchangeOpenid(code);
        AppUser user = userMapper.findByOpenid(openid).orElseGet(() -> {
            AppUser n = new AppUser();
            n.setOpenid(openid);
            userMapper.insert(n);
            return n;
        });

        String token = JwtUtil.issue(user.getUserId(), jwtSecret, parseTtlMillis(jwtExpiration));
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getUserId());
        result.put("openid", openid);
        log.info("用户登录成功: userId={}, devMode={}", user.getUserId(), devMode);
        return result;
    }

    /**
     * 检查登录状态（由 AuthInterceptor 保证已登录）
     */
    @GetMapping("/check")
    public Map<String, Object> check() {
        Map<String, Object> result = new HashMap<>();
        result.put("authenticated", true);
        result.put("userId", AuthContext.userId());
        return result;
    }

    // ========== 内部方法 ==========

    private String exchangeOpenid(String code) {
        try {
            String uri = String.format(
                    "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                    appId, appSecret, URLEncoder.encode(code, StandardCharsets.UTF_8));
            JsonNode wx = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (wx != null && wx.hasNonNull("openid")) {
                return wx.get("openid").asText();
            }
            String err = wx != null && wx.has("errmsg") ? wx.get("errmsg").asText() : "未知错误";
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "微信登录失败: " + err);
        } catch (ResponseStatusException e) {
            if (devMode) {
                log.warn("DEV MODE: code2session 失败({})，使用 dev_openid 兜底", e.getReason());
                return "dev_openid_" + code;
            }
            throw e;
        } catch (Exception e) {
            log.error("微信 code2session 调用异常", e);
            if (devMode) {
                log.warn("DEV MODE: code2session 异常，使用 dev_openid 兜底");
                return "dev_openid_" + code;
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "微信登录失败");
        }
    }

    /** 解析 "7d"/"12h"/"30m"/"15s" 形式的有效期配置 */
    private long parseTtlMillis(String expr) {
        String s = expr == null ? "7d" : expr.trim().toLowerCase();
        if (s.endsWith("d")) return Long.parseLong(s.substring(0, s.length() - 1)) * 24L * 3600_000L;
        if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 3600_000L;
        if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60_000L;
        if (s.endsWith("s")) return Long.parseLong(s.substring(0, s.length() - 1)) * 1000L;
        return Long.parseLong(s) * 1000L;
    }
}
