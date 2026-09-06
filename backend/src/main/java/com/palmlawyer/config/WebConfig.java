// WebConfig.java
// Web 配置：CORS（servlet 相对路径，与 context-path 解耦）+ 统一 JWT 拦截器
package com.palmlawyer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${palmlawyer.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Value("${palmlawyer.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private List<String> allowedMethods;

    @Value("${palmlawyer.cors.allowed-headers:*}")
    private String allowedHeaders;

    @Value("${palmlawyer.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${palmlawyer.cors.max-age:3600}")
    private long maxAge;

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 注意：控制器路径相对 context-path，因此映射必须用 servlet 相对路径 /**，
        // 之前的 /api/** 因 context-path=/api/v1 永远匹配不到。
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .allowedMethods(allowedMethods.toArray(new String[0]))
                .allowedHeaders(allowedHeaders)
                .allowCredentials(allowCredentials)
                .maxAge(maxAge);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                // /actuator/** 为探针端点，调用方无法携带用户 token，必须放行
                .excludePathPatterns("/auth/login", "/error", "/actuator/**");
    }
}
