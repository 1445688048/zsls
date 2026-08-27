package com.palmlawyer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 配置类
 * 
 * <p>从 application.yml 读取配置，创建 ChatModel Bean。
 * 支持 OpenAI 兼容的 API 供应商（如 Agnes、DeepSeek 等）。
 * 
 * <p>使用 Spring AI 2.0.1 的官方 OpenAI SDK 集成方式：
 * OpenAiChatModel.builder() 会在 build() 时根据 OpenAiChatOptions
 * 中的 baseUrl/apiKey 自动创建 OpenAI 客户端（同步 + 异步）。
 */
@Slf4j
@Configuration
public class LLMConfig {

    @Value("${palmlawyer.llm.api-key}")
    private String apiKey;

    @Value("${palmlawyer.llm.base-url}")
    private String baseUrl;

    @Value("${palmlawyer.llm.model}")
    private String model;

    @Value("${palmlawyer.llm.temperature}")
    private Double temperature;

    @Value("${palmlawyer.llm.max-tokens}")
    private Integer maxTokens;

    /**
     * 创建 ChatModel Bean
     * 
     * <p>使用 OpenAI 兼容 API，支持任意 OpenAI 格式的供应商。
     * 
     * <p>配置项在 OpenAiChatOptions 中设置 baseUrl 和 apiKey，
     * 由 OpenAiChatModel.Builder.build() 自动创建客户端。
     * 
     * @return ChatModel 实例
     */
    @Bean
    public ChatModel chatModel() {
        log.info("初始化 ChatModel: baseUrl={}, model={}", baseUrl, model);
        
        // 创建配置选项（含 baseUrl/apiKey，供 build() 自动创建客户端）
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .model(model)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .build();
        
        // 创建 ChatModel（build() 内部根据 options 自动创建 OpenAI 客户端）
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .options(options)
            .build();
        
        log.info("ChatModel 初始化完成");
        return chatModel;
    }
}
