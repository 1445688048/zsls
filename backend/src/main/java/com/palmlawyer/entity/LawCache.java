// LawCache.java
// 法律之星查询缓存实体
package com.palmlawyer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 法律之星查询缓存实体
 *
 * <p>对应数据库表：law_cache
 * <p>缓存 API 查询结果，减少重复请求。
 */
@Data
@TableName(value = "law_cache", autoResultMap = true)
public class LawCache {

    /** 缓存键（SHA-256 哈希） */
    @TableId
    private String cacheKey;

    /** 数据来源 */
    @TableField("source")
    private String source;

    /** 响应 JSON */
    @TableField(value = "response_json", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Map<String, Object> responseJson;

    /** 过期时间 */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}