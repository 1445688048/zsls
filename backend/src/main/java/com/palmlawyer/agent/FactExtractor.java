// FactExtractor.java
// 事实提取器：从 LLM 回复中提取结构化事实，统一 {value, confidence} 形状
package com.palmlawyer.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 事实提取器
 *
 * <p>从 LLM 回复中提取结构化事实数据，用于更新案件的事实要素。
 * <p>提取规则：
 * <ol>
 *   <li>优先解析 ```json 代码围栏中的 JSON；</li>
 *   <li>否则扫描所有顶层平衡花括号块，倒序尝试解析；</li>
 *   <li>facts 值统一包装为 {value, confidence}，与既有数据结构一致。</li>
 * </ol>
 */
@Slf4j
@Component
public class FactExtractor {

    private static final String FACT_SECTION = "facts";
    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 从 LLM 回复中提取事实
     *
     * @param llmResponse   LLM 回复文本
     * @param existingFacts 已有事实
     * @return 更新后的事实 Map（值均为 {value, confidence} 结构）
     */
    public Map<String, Object> extract(String llmResponse, Map<String, Object> existingFacts) {
        Map<String, Object> updated = new HashMap<>(existingFacts != null ? existingFacts : new HashMap<>());
        if (llmResponse == null || llmResponse.isBlank()) {
            return updated;
        }

        JsonNode factsNode = findFactsNode(llmResponse);
        if (factsNode == null) {
            String preview = llmResponse.length() > 200 ? llmResponse.substring(0, 200) : llmResponse;
            log.warn("[FACT_EXTRACT] 未在回复中找到可解析的事实 JSON，回复前 200 字符: {}", preview);
            return updated;
        }

        factsNode.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode raw = entry.getValue();
            Object existing = updated.get(key);
            // 只更新：字段尚无值，或新值为高置信度
            if (existing == null || isHighConfidence(raw)) {
                updated.put(key, wrapFact(raw));
            }
        });

        log.info("[FACT_EXTRACT] 提取到 {} 个事实字段", updated.size());
        return updated;
    }

    // ========== 内部方法 ==========

    /**
     * 定位 facts 节点：优先围栏 JSON，其次平衡花括号块
     */
    private JsonNode findFactsNode(String text) {
        Matcher m = FENCE.matcher(text);
        while (m.find()) {
            JsonNode facts = extractFactsFrom(tryParse(m.group(1)));
            if (facts != null) return facts;
        }

        List<String> blocks = extractBalancedBlocks(text);
        for (int i = blocks.size() - 1; i >= 0; i--) {
            JsonNode facts = extractFactsFrom(tryParse(blocks.get(i)));
            if (facts != null) return facts;
        }
        return null;
    }

    private JsonNode extractFactsFrom(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        JsonNode facts = node.has(FACT_SECTION) ? node.get(FACT_SECTION) : node;
        return facts != null && facts.isObject() ? facts : null;
    }

    private JsonNode tryParse(String s) {
        try {
            return mapper.readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 栈深扫描所有顶层平衡花括号块（按文本顺序返回）
     */
    private List<String> extractBalancedBlocks(String text) {
        List<String> blocks = new ArrayList<>();
        int depth = 0;
        int start = -1;
        char[] cs = text.toCharArray();
        for (int i = 0; i < cs.length; i++) {
            char c = cs[i];
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                if (depth > 0) depth--;
                if (depth == 0 && start >= 0) {
                    blocks.add(text.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return blocks;
    }

    private boolean isHighConfidence(JsonNode node) {
        return node.isObject() && node.has("confidence")
                && "HIGH".equals(node.get("confidence").asText());
    }

    /**
     * 包装为统一结构 {value, confidence}
     */
    private Map<String, Object> wrapFact(JsonNode raw) {
        Object value;
        String confidence;
        if (raw.isObject() && raw.has("value")) {
            value = scalar(raw.get("value"));
            confidence = raw.has("confidence") ? raw.get("confidence").asText("LOW") : "LOW";
        } else {
            value = scalar(raw);
            confidence = "LOW";
        }
        Map<String, Object> fact = new HashMap<>();
        fact.put("value", value);
        fact.put("confidence", confidence);
        return fact;
    }

    private Object scalar(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isBoolean()) return n.asBoolean();
        if (n.isNumber()) return n.numberValue();
        if (n.isTextual()) return n.asText();
        return n.toString();
    }
}