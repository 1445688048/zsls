// EvidenceAdvisor.java
// 证据顾问，根据案件事实推荐需要收集的证据
package com.palmlawyer.agent;

import com.palmlawyer.service.infra.plugin.DomainPlugin;
import com.palmlawyer.service.infra.plugin.DomainPluginLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 证据顾问
 *
 * <p>根据案件事实和领域插件的证据规则，
 * 推荐用户需要收集的证据清单。
 */
@Slf4j
@Component
public class EvidenceAdvisor {

    private final DomainPluginLoader pluginLoader;

    public EvidenceAdvisor(DomainPluginLoader pluginLoader) {
        this.pluginLoader = pluginLoader;
    }

    /**
     * 生成证据建议
     *
     * @param state                案件状态（facts 用于 applicableWhen 条件求值）
     * @param collectedEvidenceIds 已收集证据的 id 集合（来自 case_profile.evidenceRefs 中 collected=true 的 refId）
     */
    public EvidenceAdvice advise(AgentState state, Set<String> collectedEvidenceIds) {
        DomainPlugin plugin = pluginLoader.getPlugin(state.getDomainType());
        EvidenceAdvice advice = new EvidenceAdvice();
        Set<String> collected = collectedEvidenceIds != null ? collectedEvidenceIds : Set.of();

        if (plugin == null || plugin.getEvidenceRules() == null) {
            log.debug("领域插件或证据规则不存在: {}", state.getDomainType());
            return advice;
        }

        // 检查必要证据
        List<DomainPlugin.EvidenceItem> necessary = plugin.getEvidenceRules().getNecessary();
        if (necessary != null) {
            for (DomainPlugin.EvidenceItem item : necessary) {
                if (isMissing(state.getFacts(), item, collected)) {
                    advice.getMissingNecessary().add(item);
                }
            }
        }

        // 检查增强证据
        List<DomainPlugin.EvidenceItem> enhancing = plugin.getEvidenceRules().getEnhancing();
        if (enhancing != null) {
            for (DomainPlugin.EvidenceItem item : enhancing) {
                if (isApplicable(state.getFacts(), item) && isMissing(state.getFacts(), item, collected)) {
                    advice.getMissingEnhancing().add(item);
                }
            }
        }

        // 检查早期预警
        advice.setEarlyWarnings(checkEarlyWarnings(state));

        return advice;
    }

    /**
     * 判断证据是否缺失：evidenceRefs 中已标记收集，或 facts 中存在 evidence_<id> 标记，均视为已收集
     */
    private boolean isMissing(Map<String, Object> facts, DomainPlugin.EvidenceItem item, Set<String> collectedEvidenceIds) {
        if (collectedEvidenceIds.contains(item.getId())) {
            return false;
        }
        if (facts.get("evidence_" + item.getId()) != null) {
            return false;
        }
        return isApplicable(facts, item);
    }

    /**
     * 判断证据是否适用（applicableWhen 条件求值）
     */
    private boolean isApplicable(Map<String, Object> facts, DomainPlugin.EvidenceItem item) {
        String condition = item.getApplicableWhen();
        if (condition == null || condition.isBlank()) {
            return true;
        }
        try {
            return evaluateCondition(condition, facts);
        } catch (Exception e) {
            log.warn("解析适用条件失败: {}，默认适用", condition);
            return true;
        }
    }

    /**
     * 条件求值器
     *
     * <p>支持：AND / OR 组合；!=、IN、= 单条件；字段值兼容 {value, confidence} 包装
     */
    private boolean evaluateCondition(String condition, Map<String, Object> facts) {
        if (condition.contains(" AND ")) {
            for (String part : condition.split(" AND ")) {
                if (!evaluateCondition(part.trim(), facts)) {
                    return false;
                }
            }
            return true;
        }
        if (condition.contains(" OR ")) {
            for (String part : condition.split(" OR ")) {
                if (evaluateCondition(part.trim(), facts)) {
                    return true;
                }
            }
            return false;
        }

        condition = condition.trim();

        // != 条件（必须先于 = 判断，否则 field != 'x' 会被 = 分支误截断字段名）
        if (condition.contains("!=")) {
            String[] parts = condition.split("!=", 2);
            String field = parts[0].trim();
            String expected = parts.length > 1 ? parts[1].trim().replace("'", "").replace("\"", "") : "";
            Object factValue = unwrapFactValue(facts.get(field));
            if (factValue == null) return true;
            return !factValue.toString().equals(expected);
        }

        // IN 条件
        if (condition.contains(" IN ")) {
            String[] parts = condition.split(" IN ", 2);
            String field = parts[0].trim();
            String values = parts.length > 1 ? parts[1].trim().replace("[", "").replace("]", "") : "";
            List<String> valueList = Arrays.stream(values.split(","))
                .map(v -> v.trim().replace("'", "").replace("\"", ""))
                .toList();
            Object factValue = unwrapFactValue(facts.get(field));
            if (factValue == null) return false;
            return valueList.contains(factValue.toString());
        }

        // = 条件（只切一次，防止比较值中出现 = 号导致越界）
        if (condition.contains("=")) {
            String[] parts = condition.split("=", 2);
            String field = parts[0].trim();
            String expected = parts.length > 1 ? parts[1].trim() : "";
            Object factValue = unwrapFactValue(facts.get(field));
            if (factValue == null) return false;

            if (expected.equalsIgnoreCase("true")) {
                return Boolean.TRUE.equals(factValue) || "true".equalsIgnoreCase(factValue.toString());
            }
            if (expected.equalsIgnoreCase("false")) {
                return Boolean.FALSE.equals(factValue) || "false".equalsIgnoreCase(factValue.toString());
            }

            return factValue.toString().equals(expected.replace("'", "").replace("\"", ""));
        }

        return true;
    }

    /**
     * 解包事实值：兼容 {value, confidence} 包装结构与标量两种形态
     */
    private Object unwrapFactValue(Object factValue) {
        if (factValue instanceof Map) {
            Object v = ((Map<?, ?>) factValue).get("value");
            if (v != null) return v;
        }
        if (factValue instanceof com.fasterxml.jackson.databind.JsonNode node) {
            if (node.isObject() && node.has("value")) {
                return node.get("value").asText();
            }
            return node.asText();
        }
        return factValue;
    }

    /**
     * 检查早期预警
     */
    private List<Map<String, String>> checkEarlyWarnings(AgentState state) {
        List<Map<String, String>> warnings = new ArrayList<>();

        String userInput = "";
        Object lastMsg = state.getFacts().get("_lastUserMessage");
        if (lastMsg != null) {
            userInput = lastMsg.toString();
        }

        DomainPlugin plugin = pluginLoader.getPlugin(state.getDomainType());
        if (plugin == null || plugin.getEvidenceRules() == null || plugin.getEvidenceRules().getEarlyWarning() == null) {
            return warnings;
        }

        for (DomainPlugin.EarlyWarning warning : plugin.getEvidenceRules().getEarlyWarning()) {
            List<String> keywords = warning.getTriggerKeywords();
            if (keywords == null) continue;
            for (String keyword : keywords) {
                if (userInput.contains(keyword)) {
                    Map<String, String> warningMap = new LinkedHashMap<>();
                    warningMap.put("warningText", warning.getWarningText());
                    warningMap.put("keywords", keyword);
                    if (warning.getEvidenceToCollect() != null) {
                        warningMap.put("evidenceToCollect", String.join("、", warning.getEvidenceToCollect()));
                    }
                    warnings.add(warningMap);
                    break;
                }
            }
        }

        return warnings;
    }

    /**
     * 证据建议结果
     */
    public static class EvidenceAdvice {
        private List<DomainPlugin.EvidenceItem> missingNecessary = new ArrayList<>();
        private List<DomainPlugin.EvidenceItem> missingEnhancing = new ArrayList<>();
        private List<Map<String, String>> earlyWarnings = new ArrayList<>();

        public List<DomainPlugin.EvidenceItem> getMissingNecessary() { return missingNecessary; }
        public List<DomainPlugin.EvidenceItem> getMissingEnhancing() { return missingEnhancing; }
        public List<Map<String, String>> getEarlyWarnings() { return earlyWarnings; }
        public void setEarlyWarnings(List<Map<String, String>> warnings) { this.earlyWarnings = warnings; }
        public boolean hasMissingNecessary() { return !missingNecessary.isEmpty(); }
        public boolean hasEarlyWarnings() { return !earlyWarnings.isEmpty(); }
    }
}