// AgentState.java
// Agent 对话状态类，封装对话过程中的中间状态
package com.palmlawyer.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 对话状态
 *
 * <p>封装 Agent 在对话过程中的关键状态信息，
 * 用于各组件之间传递上下文。
 */
public class AgentState {

    /** 案件领域类型（LABOR、CONSUMER 等） */
    private final String domainType;

    /** 已收集的事实要素 */
    private final Map<String, Object> facts;

    /** 已识别的法律问题 */
    private final List<String> issues;

    /** 证据收集状态 */
    private final List<Map<String, Object>> evidence;

    /**
     * 构造函数
     *
     * @param domainType 领域类型
     * @param facts      事实要素
     */
    public AgentState(String domainType, Map<String, Object> facts) {
        this.domainType = domainType;
        this.facts = facts != null ? facts : new java.util.HashMap<>();
        this.issues = new ArrayList<>();
        this.evidence = new ArrayList<>();
    }

    /** 获取领域类型 */
    public String getDomainType() {
        return domainType;
    }

    /** 获取事实要素 */
    public Map<String, Object> getFacts() {
        return facts;
    }

    /** 获取已识别的法律问题 */
    public List<String> getIssues() {
        return issues;
    }

    /** 获取证据收集状态 */
    public List<Map<String, Object>> getEvidence() {
        return evidence;
    }

    /**
     * 检查是否已收集足够事实
     *
     * @return true 如果事实收集基本完整
     */
    public boolean hasEnoughFacts() {
        // 简化版：至少有 3 个事实
        return facts.size() >= 3;
    }

    /**
     * 获取缺失的必要事实数量
     *
     * @return 缺失数量
     */
    public int getMissingFactCount() {
        // TODO: 根据领域插件的 factSchema 计算
        return 0;
    }
}
