// H2ConsoleConfig.java
// H2 控制台手动注册（仅当 palmlawyer.h2-console.enabled=true 时创建，默认关闭）
// 说明：H2 依赖为 runtime scope，编译期不可见 org.h2.server.web 包，
// 因此通过反射加载 servlet 类，运行时 H2 在 classpath 上可见。
package com.palmlawyer.config;

import jakarta.servlet.Servlet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * H2 控制台配置（仅开发环境）
 *
 * <p>通过 {@code palmlawyer.h2-console.enabled} 开关控制，默认不创建 Bean，
 * 生产环境移除 h2 依赖也不会影响启动（与注释语义一致）。
 */
@Slf4j
@Configuration
public class H2ConsoleConfig {

    @Bean
    @ConditionalOnProperty(name = "palmlawyer.h2-console.enabled", havingValue = "true", matchIfMissing = false)
    public ServletRegistrationBean<Servlet> h2ConsoleServletRegistration() {
        try {
            Class<?> servletClass = Class.forName("org.h2.server.web.JakartaWebServlet");
            Servlet servlet = (Servlet) servletClass.getDeclaredConstructor().newInstance();
            ServletRegistrationBean<Servlet> registration =
                    new ServletRegistrationBean<>(servlet, "/h2-console/*");
            registration.setName("H2Console");
            registration.setLoadOnStartup(1);
            log.info("H2 控制台已注册: /h2-console");
            return registration;
        } catch (Exception e) {
            log.warn("H2 控制台注册失败（不影响启动）: {}", e.getMessage());
            return null;
        }
    }
}
