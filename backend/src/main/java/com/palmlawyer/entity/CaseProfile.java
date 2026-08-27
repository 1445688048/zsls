// CaseProfile.java
// 案件档案实体类，映射 case_profile 表
package com.palmlawyer.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 案件档案实体
 *
 * <p>对应数据库表：case_profile
 * <p>存储用户创建的每个案件的基本信息和结构化数据。
 *
 * <p>JSON 字段说明：
 * <ul>
 *   <li>facts_json - 事实要素 JSON，key 为字段名，value 为 FactValue 对象</li>
 *   <li>issues_json - 已识别的法律问题列表</li>
 *   <li>laws_json - 引用的法律条文列表</li>
 *   <li>timeline_json - 时间轴事件列表</li>
 *   <li>evidence_refs - 证据引用列表</li>
 * </ul>
 */
@Data
@TableName(value = "case_profile", autoResultMap = true)
public class CaseProfile {

    /** 案件 ID，自增主键 */
    @TableId(type = IdType.AUTO)
    private Long caseId;

    /** 所属用户 ID */
    @TableField("user_id")
    private Long userId;

    /**
     * 案件领域类型
     * <ul>
     *   <li>LABOR - 劳动纠纷</li>
     *   <li>CONSUMER - 消费维权</li>
     * </ul>
     */
    @TableField("domain_type")
    private String domainType;

    /**
     * 案件状态
     * <ul>
     *   <li>COLLECTING - 事实采集中</li>
     *   <li>ANALYZING - 分析中</li>
     *   <li>READY - 可导出</li>
     * </ul>
     */
    @TableField("status")
    private String status;

    /** 案件标题 */
    @TableField("title")
    private String title;

    /**
     * 事实要素 JSON
     * Map&lt;String, FactValue&gt;，key 为字段名，value 为事实值对象
     */
    @TableField(value = "facts_json", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Map<String, Object> factsJson;

    /** 已识别的法律问题列表 */
    @TableField(value = "issues_json", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<String> issuesJson;

    /** 引用的法律条文列表 */
    @TableField(value = "laws_json", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<Map<String, Object>> lawsJson;

    /** 时间轴事件列表 */
    @TableField(value = "timeline_json", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<Map<String, Object>> timelineJson;

    /** 证据引用列表 */
    @TableField(value = "evidence_refs", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<Map<String, Object>> evidenceRefs;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}