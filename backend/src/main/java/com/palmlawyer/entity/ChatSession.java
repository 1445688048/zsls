// ChatSession.java
// 对话会话实体类
package com.palmlawyer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话实体
 *
 * <p>对应数据库表：chat_session
 * <p>每个会话对应一个案件的一段对话历史。
 */
@Data
@TableName("chat_session")
public class ChatSession {

    /** 会话 ID，自增主键 */
    @TableId(type = IdType.AUTO)
    private Long sessionId;

    /** 关联案件 ID */
    @TableField("case_id")
    private Long caseId;

    /** 用户 ID */
    @TableField("user_id")
    private Long userId;

    /** 会话摘要 */
    @TableField("summary")
    private String summary;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
