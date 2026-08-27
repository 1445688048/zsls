// DomainKnowledgeVector.java
// 领域知识向量实体（向量知识库表）
package com.palmlawyer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 领域知识向量实体
 *
 * <p>对应数据库表：domain_knowledge_vector
 * <p>存储领域知识的向量表示，用于向量检索。
 * <p>H2 开发环境将向量以文本形式存储，生产环境迁移到 PostgreSQL + pgvector。
 */
@Data
@TableName(value = "domain_knowledge_vector", autoResultMap = true)
public class DomainKnowledgeVector {

    /** 向量 ID，自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 领域类型 */
    @TableField("domain_type")
    private String domainType;

    /** 内容类型（如 LAW_ARTICLE、FAQ 等） */
    @TableField("content_type")
    private String contentType;

    /** 内容 ID */
    @TableField("content_id")
    private String contentId;

    /** 知识文本块 */
    @TableField("text_chunk")
    private String textChunk;

    /**
     * 向量文本（H2 开发环境）
     * <p>生产环境 PostgreSQL 使用 pgvector 的 vector 类型
     */
    @TableField("embedding_text")
    private String embeddingText;

    /** 元数据 JSON */
    @TableField(value = "metadata_json", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
