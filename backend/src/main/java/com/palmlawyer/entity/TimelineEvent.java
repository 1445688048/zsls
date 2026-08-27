// TimelineEvent.java
// 时间轴事件实体，映射 timeline_event 表（替代 case_profile.timeline_json 整条读写）
package com.palmlawyer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("timeline_event")
public class TimelineEvent {

    /** 事件 ID（UUID） */
    @TableId
    private String eventId;

    /** 所属案件 ID */
    @TableField("case_id")
    private Long caseId;

    /** 事件发生时间（前端格式，如 2024-01-01） */
    @TableField("event_time")
    private String eventTime;

    /** 事件描述 */
    @TableField("description")
    private String description;

    /** 事件类型：USER_ACTION / LAW_ACTION / MILESTONE 等 */
    @TableField("type")
    private String type;

    /** 是否期限提醒 */
    @TableField("is_deadline")
    private Boolean isDeadline;

    /** 提醒文案 */
    @TableField("reminder_text")
    private String reminderText;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}