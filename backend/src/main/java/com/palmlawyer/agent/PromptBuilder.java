// PromptBuilder.java
// Prompt 构建器，负责生成 LLM 的系统 Prompt
package com.palmlawyer.agent;

import com.palmlawyer.entity.CaseProfile;
import com.palmlawyer.service.infra.plugin.DomainPlugin;
import com.palmlawyer.service.infra.plugin.DomainPluginLoader;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Prompt 构建器
 *
 * <p>根据案件状态和领域插件，动态生成 LLM 系统 Prompt。
 * <p>包含：
 * <ul>
 *   <li>角色设定和行为准则</li>
 *   <li>当前案件事实</li>
 *   <li>事实要素 Schema</li>
 *   <li>证据规则</li>
 *   <li>维权流程模板</li>
 * </ul>
 */
@Component
public class PromptBuilder {

    private final DomainPluginLoader pluginLoader;

    public PromptBuilder(DomainPluginLoader pluginLoader) {
        this.pluginLoader = pluginLoader;
    }

    /**
     * 构建系统 Prompt
     *
     * @param caseProfile 案件对象
     * @return 系统 Prompt 文本
     */
    public String build(CaseProfile caseProfile) {
        StringBuilder sb = new StringBuilder();

        // 1. 角色设定
        sb.append("你是一个专业的劳动法律师助手，服务于中国大陆用户。\n\n");

        // 2. 案件信息
        sb.append("## 当前案件\n");
        sb.append("- 领域：").append(caseProfile.getDomainType()).append("\n");
        sb.append("- 事实：").append(toJson(caseProfile.getFactsJson())).append("\n");
        sb.append("- 证据：").append(toJson(caseProfile.getEvidenceRefs())).append("\n\n");

        // 3. 行为准则
        sb.append("## 行为准则\n");
        sb.append("1. 每次最多追问 2-3 个问题\n");
        sb.append("2. 主动识别风险信号（裁员、欠薪、工伤等）\n");
        sb.append("3. 法律引用必须标注全称 + 条款号 + 原文\n");
        sb.append("4. 每次回复末尾必须附免责声明\n\n");

        // 4. 输出格式
        sb.append("## 输出格式\n");
        sb.append("### 理解确认\n简短确认理解。\n\n");
        sb.append("### 法律提示\n引用相关法条。\n\n");
        sb.append("### 证据建议\n列出建议收集的证据。\n\n");
        sb.append("### 追问\n提出 2-3 个补充问题。\n\n");
        sb.append("### 免责声明\n⚠️ 本结果由 AI 生成，仅供参考。\n\n");
        // 事实提取约定：FactExtractor 只解析围栏 JSON 中显式的 "facts" 键，
        // 模型必须严格按此格式输出新确认的事实，其余 JSON 不会被视为事实。
        sb.append("## 事实更新（可选）\n");
        sb.append("仅当本轮对话确认了新的事实时，在回复最末尾输出：\n");
        sb.append("```json\n{\"facts\": {\"字段名\": {\"value\": \"值\", \"confidence\": \"HIGH\"}}}\n```\n");
        sb.append("字段名使用事实要素 Schema 中的英文字段名；没有新事实则完全省略此节，不要输出其他 JSON。\n");

        // 5. Schema 参考
        DomainPlugin plugin = pluginLoader.getPlugin(caseProfile.getDomainType());
        if (plugin != null) {
            sb.append("\n## 事实要素 Schema\n");
            sb.append(toJson(plugin.getFactSchema())).append("\n");
        }

        return sb.toString();
    }

    /**
     * 获取缺失的事实字段
     *
     * @param state Agent 状态
     * @return 缺失的必要字段列表
     */
    public List<String> getMissingFacts(AgentState state) {
        List<String> missing = new ArrayList<>();
        // 简化版：返回所有必要但未填写的字段
        // TODO: 实现完整的字段匹配逻辑
        return missing;
    }

    /**
     * 生成追问列表
     *
     * @param state Agent 状态
     * @return 追问问题列表
     */
    public List<String> generateFollowUpQuestions(AgentState state) {
        List<String> questions = new ArrayList<>();
        // 简化版：根据领域返回默认问题
        if (state.getDomainType().equals("LABOR")) {
            if (!state.getFacts().containsKey("employmentStartDate")) {
                questions.add("您什么时候入职的？");
            }
            if (!state.getFacts().containsKey("salaryAmount")) {
                questions.add("您的月工资是多少？");
            }
            if (!state.getFacts().containsKey("terminationReason")) {
                questions.add("公司为什么辞退您？");
            }
        }
        return questions;
    }

    private String toJson(Object obj) {
        if (obj == null) return "{}";
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
