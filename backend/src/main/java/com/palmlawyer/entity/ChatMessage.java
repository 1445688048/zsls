// ChatMessage.java
// 对话消息实体类
package com.palmlawyer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 对话消息实体
 *
 * <p>对应数据库表：chat_message
 * <p>存储每轮对话的用户消息和 AI 回复。
 */
@Data
@TableName(value = "chat_message", autoResultMap = true)
public class ChatMessage {

    /** 消息 ID，自增主键 */
    @TableId(type = IdType.AUTO)
    private Long messageId;

    /** 所属会话 ID */
    @TableField("session_id")
    private Long sessionId;

    /** 消息角色：user / assistant / system */
    @TableField("role")
    private String role;

    /** 消息内容 */
    @TableField("content")
    private String content;

    /**
     * 证据引用列表（可选）
     * <p>格式：[{"evidenceId": "xxx", "type": "necessary"}]
     */
    @TableField(value = "evidence_refs", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<Map<String, Object>> evidenceRefs;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}