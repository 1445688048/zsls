// PalmLawyerApplication.java
// 掌上律师后端服务主启动类
// Spring Boot 4.0 + Spring AI 2.0 + MyBatis-Plus
package com.palmlawyer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 掌上律师后端服务主启动类
 *
 * <p>技术栈：
 * <ul>
 *   <li>Spring Boot 4.0 - 应用框架</li>
 *   <li>Spring AI 2.0 - Agent 编排、ChatModel</li>
 *   <li>MyBatis-Plus - ORM 框架</li>
 *   <li>H2 内存数据库 - 开发阶段（上线切换 PostgreSQL）</li>
 * </ul>
 *
 * <p>启动方式：
 * <pre>
 *   mvn spring-boot:run
 *   # 或直接用 IDEA 运行
 * </pre>
 *
 * <p>访问地址：
 * <ul>
 *   <li>API: http://localhost:8080/api/v1</li>
 *   <li>H2 Console: http://localhost:8080/api/v1/h2-console（手动注册，见 H2ConsoleConfig）</li>
 * </ul>
 */
@SpringBootApplication
@MapperScan("com.palmlawyer.mapper")
@ComponentScan(basePackages = "com.palmlawyer")
public class PalmLawyerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PalmLawyerApplication.class, args);
        System.out.println("========================================");
        System.out.println("掌上律师 MVP 后端服务启动成功！");
        System.out.println("H2 控制台: http://localhost:8080/api/v1/h2-console");
        System.out.println("========================================");
    }
}