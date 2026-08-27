// DisclaimerService.java
// 免责声明服务，负责生成风险提示和免责声明
package com.palmlawyer.agent;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 免责声明服务
 *
 * <p>提供：
 * <ul>
 *   <li>固定免责声明文本</li>
 *   <li>高风险案件识别</li>
 *   <li>风险警告生成</li>
 * </ul>
 */
@Service
public class DisclaimerService {

    private static final String DISCLAIMER = "⚠️ 本结果由 AI 生成，仅供参考，不构成法律意见。复杂或重大事项建议咨询专业律师。";

    /**
     * 获取免责声明文本
     */
    public String getDisclaimer() {
        return DISCLAIMER;
    }

    /**
     * 为内容追加免责声明
     */
    public String wrapWithDisclaimer(String content) {
        return content + "\n\n" + DISCLAIMER;
    }

    /**
     * 检查是否为高风险案件
     *
     * @param userMessage 用户消息
     * @param facts       已有事实
     * @return true 如果检测到高风险
     */
    public boolean isHighRisk(String userMessage, Map<String, Object> facts) {
        String msg = (userMessage != null ? userMessage : "").toLowerCase();
        if (msg.contains("辞退") || msg.contains("开除") || msg.contains("裁员")) return true;
        if (msg.contains("工伤") || msg.contains("受伤")) return true;
        if (msg.contains("欠薪") || msg.contains("拖欠工资")) return true;
        return false;
    }

    /**
     * 生成风险警告文本
     */
    public String getRiskWarning(String userMessage, Map<String, Object> facts) {
        if (!isHighRisk(userMessage, facts)) {
            return "";
        }
        StringBuilder sb = new StringBuilder("⚠️ **风险提示**\n\n");
        String msg = (userMessage != null ? userMessage : "").toLowerCase();
        if (msg.contains("辞退") || msg.contains("开除")) {
            sb.append("- 涉及违法解除风险，请注意收集证据\n");
        }
        if (msg.contains("工伤")) {
            sb.append("- 涉及工伤认定风险，请及时申报\n");
        }
        if (msg.contains("欠薪")) {
            sb.append("- 涉及恶意欠薪风险，建议尽快维权\n");
        }
        sb.append("\n---\n");
        return sb.toString();
    }
}
