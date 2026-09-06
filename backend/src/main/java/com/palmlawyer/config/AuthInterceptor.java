// AuthInterceptor.java
// 统一 JWT 鉴权拦截器：校验 Authorization: Bearer <token>，通过后写入 AuthContext
package com.palmlawyer.config;

import com.palmlawyer.util.AuthContext;
import com.palmlawyer.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtSecretProvider jwtSecretProvider;

    public AuthInterceptor(JwtSecretProvider jwtSecretProvider) {
        this.jwtSecretProvider = jwtSecretProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        // CORS 预检放行（由 CORS 配置处理）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        // <image>/wx.previewImage 无法携带 Authorization 头，
        // 文件读取类 GET 允许通过 ?token= 查询参数传递（其余接口仍只认 header）
        if ((auth == null || !auth.startsWith("Bearer ")) && isFileRead(request)) {
            String token = request.getParameter("token");
            if (token != null && !token.isBlank()) {
                auth = "Bearer " + token;
            }
        }
        if (auth == null || !auth.startsWith("Bearer ")) {
            AuthContext.clear();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        try {
            Long userId = JwtUtil.verify(auth.substring(7), jwtSecretProvider.get());
            AuthContext.set(userId);
            return true;
        } catch (Exception e) {
            AuthContext.clear();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已过期或无效");
        }
    }

    private boolean isFileRead(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = context != null && uri.startsWith(context) ? uri.substring(context.length()) : uri;
        return path.startsWith("/files/");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }
}
