// ChatService.java
// 对话服务层：Agent 编排（系统提示词 + DB 历史窗口 + 免责声明尾帧 + 事实回写）
package com.palmlawyer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.palmlawyer.agent.DisclaimerService;
import com.palmlawyer.agent.EvidenceAdvisor;
import com.palmlawyer.agent.FactExtractor;
import com.palmlawyer.agent.PromptBuilder;
import com.palmlawyer.agent.AgentState;
import com.palmlawyer.entity.CaseProfile;
import com.palmlawyer.entity.ChatMessage;
import com.palmlawyer.entity.ChatSession;
import com.palmlawyer.mapper.CaseProfileMapper;
import com.palmlawyer.mapper.ChatMessageMapper;
import com.palmlawyer.mapper.ChatSessionMapper;
import com.palmlawyer.service.infra.law.dto.LawStarResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对话服务
 *
 * <p>实际对话链路（ChatController -> chatStream）：
 * <ol>
 *   <li>载入案件，PromptBuilder 构建系统提示词（含领域插件 Schema 与法条参考）；</li>
 *   <li>从 DB 加载最近 N 条历史构上下文（DB 为唯一事实源，重启不丢记忆）；</li>
 *   <li>流式调用 LLM，末尾追加免责声明帧（上屏与落库一致，兑现合规承诺）；</li>
 *   <li>完成后回写事实提取与证据建议到案件档案。</li>
 * </ol>
 */
@Slf4j
@Service
public class ChatService {

    private static final int HISTORY_WINDOW = 20;
    private static final int LAW_REF_TOPK = 3;

    private final ChatModel chatModel;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final CaseProfileMapper caseMapper;
    private final LawService lawService;
    private final PromptBuilder promptBuilder;
    private final FactExtractor factExtractor;
    private final EvidenceAdvisor evidenceAdvisor;
    private final DisclaimerService disclaimerService;
    private final CaseStatusService caseStatusService;

    public ChatService(ChatModel chatModel,
                       ChatSessionMapper sessionMapper,
                       ChatMessageMapper messageMapper,
                       CaseProfileMapper caseMapper,
                       LawService lawService,
                       PromptBuilder promptBuilder,
                       FactExtractor factExtractor,
                       EvidenceAdvisor evidenceAdvisor,
                       DisclaimerService disclaimerService,
                       CaseStatusService caseStatusService) {
        this.chatModel = chatModel;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.caseMapper = caseMapper;
        this.lawService = lawService;
        this.promptBuilder = promptBuilder;
        this.factExtractor = factExtractor;
        this.evidenceAdvisor = evidenceAdvisor;
        this.disclaimerService = disclaimerService;
        this.caseStatusService = caseStatusService;
    }

    /**
     * 创建新会话
     */
    public ChatSession createSession(Long caseId, Long userId) {
        ChatSession s = new ChatSession();
        s.setCaseId(caseId);
        s.setUserId(userId);
        s.setSummary("");
        s.setCreatedAt(LocalDateTime.now());
        sessionMapper.insert(s);
        log.info("创建新会话: sessionId={}, caseId={}, userId={}", s.getSessionId(), caseId, userId);
        return s;
    }

    /**
     * 查询会话（含属主校验用），不存在返回 null
     */
    public ChatSession getSession(Long sessionId) {
        return sessionMapper.selectById(sessionId);
    }

    /**
     * 流式对话（SSE）：Agent 编排主流程
     */
    public Flux<String> chatStream(Long sessionId, String userMessage) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        CaseProfile caseProfile = caseMapper.selectById(session.getCaseId());
        if (caseProfile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "案件不存在");
        }

        saveMessage(sessionId, "user", userMessage);

        // 1) 系统提示词（角色 + 案件事实 + 领域 Schema + 本题参考法条）
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(buildSystemPrompt(caseProfile, userMessage)));

        // 2) DB 历史窗口（跳过刚保存的当前消息，避免重复）
        List<ChatMessage> history = messageMapper.selectBySessionId(sessionId);
        int skip = Math.max(0, history.size() - 1 - HISTORY_WINDOW);
        for (int i = skip; i < history.size() - 1; i++) {
            ChatMessage m = history.get(i);
            if ("user".equals(m.getRole())) {
                messages.add(new UserMessage(m.getContent()));
            } else {
                messages.add(new AssistantMessage(m.getContent()));
            }
        }
        messages.add(new UserMessage(userMessage));

        // 3) 流式输出 + 免责声明尾帧
        StringBuilder full = new StringBuilder();
        return Flux.concat(
                chatModel.stream(new Prompt(messages)).map(this::extractText).filter(s -> !s.isEmpty()),
                Flux.just("\n\n" + disclaimerService.getDisclaimer()))
            .doOnNext(full::append)
            .doOnComplete(() -> afterAnswer(sessionId, caseProfile, userMessage, full.toString()));
    }

    /**
     * 非流式对话（测试/兜底场景），复用同一编排
     */
    public String chat(Long sessionId, String userMessage) {
        StringBuilder full = new StringBuilder();
        chatStream(sessionId, userMessage).blockLast();
        // blockLast 后需重新读取助手消息（流式路径已保存），此处返回最新一条
        List<ChatMessage> msgs = messageMapper.selectBySessionId(sessionId);
        if (!msgs.isEmpty()) {
            ChatMessage last = msgs.get(msgs.size() - 1);
            if ("assistant".equals(last.getRole())) {
                full.append(last.getContent());
            }
        }
        return full.toString();
    }

    /**
     * 获取会话历史消息
     */
    public List<ChatMessage> getSessionMessages(Long sessionId) {
        return messageMapper.selectBySessionId(sessionId);
    }

    /**
     * 清除会话历史（真删 DB，行为与方法名一致）
     */
    public void clearSessionHistory(Long sessionId) {
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
        log.info("会话 {} 历史已清空(DB)", sessionId);
    }

    // ========== 内部方法 ==========

    /**
     * 构建系统提示词：领域 Schema + 本题参考法条（top3）
     */
    private String buildSystemPrompt(CaseProfile caseProfile, String userMessage) {
        StringBuilder sb = new StringBuilder(promptBuilder.build(caseProfile));
        try {
            List<LawStarResponse.ArticleResult> refs = lawService.search(userMessage, "全国");
            if (refs != null && !refs.isEmpty()) {
                sb.append("\n## 本题参考法条\n");
                refs.stream().limit(LAW_REF_TOPK).forEach(r ->
                        sb.append("- ").append(r.getLawTitle()).append(" ").append(r.getArticle())
                          .append(": ").append(r.getContent()).append("\n"));
            }
        } catch (Exception e) {
            log.warn("法条检索注入失败(不影响对话): {}", e.getMessage());
        }
        return sb.toString();
    }

    private String extractText(ChatResponse response) {
        if (response.getResult() != null && response.getResult().getOutput() != null) {
            String text = response.getResult().getOutput().getText();
            return text != null ? text : "";
        }
        return "";
    }

    /**
     * 流式完成后的落库与案件回写（各子步骤独立 try/catch，互不拖累）
     */
    private void afterAnswer(Long sessionId, CaseProfile caseProfile, String userMessage, String fullResponse) {
        if (fullResponse == null || fullResponse.isEmpty()) {
            log.warn("会话 {} 无输出内容，跳过落库", sessionId);
            return;
        }
        try {
            saveMessage(sessionId, "assistant", fullResponse);
            log.info("会话 {} 助手响应已保存，长度: {}", sessionId, fullResponse.length());
        } catch (Exception e) {
            log.error("保存助手消息失败: sessionId={}", sessionId, e);
        }

        try {
            Map<String, Object> facts = factExtractor.extract(fullResponse, caseProfile.getFactsJson());
            if (!facts.isEmpty()) {
                caseProfile.setFactsJson(facts);
                log.info("会话 {} 案件事实已提取: {} 个字段", sessionId, facts.size());
            }
        } catch (Exception e) {
            log.error("事实提取回写失败: sessionId={}", sessionId, e);
        }

        // 本轮引用法条合并落库（F3）
        mergeLawRefs(caseProfile, userMessage);

        // 统一落库 + 状态重估（F4）
        try {
            caseProfile.setUpdatedAt(LocalDateTime.now());
            caseMapper.updateById(caseProfile);
            caseStatusService.reevaluate(caseProfile.getCaseId());
        } catch (Exception e) {
            log.error("案件档案回写/状态重估失败: sessionId={}", sessionId, e);
        }

        try {
            AgentState state = new AgentState(caseProfile.getDomainType(), caseProfile.getFactsJson());
            EvidenceAdvisor.EvidenceAdvice advice = evidenceAdvisor.advise(state);
            log.info("[AGENT] 会话 {} 证据建议: 必要缺失={}, 增强缺失={}, 预警={}",
                    sessionId, advice.getMissingNecessary().size(),
                    advice.getMissingEnhancing().size(), advice.getEarlyWarnings().size());
        } catch (Exception e) {
            log.error("证据建议生成失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 将本轮检索到的引用法条去重合并进案件 lawsJson（F3：让"法律意见"有数据可展示）
     */
    private void mergeLawRefs(CaseProfile caseProfile, String userMessage) {
        try {
            List<LawStarResponse.ArticleResult> refs = lawService.search(userMessage, "全国");
            if (refs == null || refs.isEmpty()) {
                return;
            }
            List<Map<String, Object>> existing = new ArrayList<>(
                    caseProfile.getLawsJson() != null ? caseProfile.getLawsJson() : new ArrayList<>());
            Set<String> seen = new HashSet<>();
            for (Map<String, Object> m : existing) {
                seen.add(m.get("lawTitle") + "|" + m.get("article"));
            }
            refs.stream().limit(LAW_REF_TOPK).forEach(r -> {
                String key = r.getLawTitle() + "|" + r.getArticle();
                if (!seen.contains(key)) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("lawTitle", r.getLawTitle());
                    item.put("article", r.getArticle());
                    item.put("content", r.getContent());
                    item.put("relevance", r.getRelevanceScore());
                    existing.add(item);
                    seen.add(key);
                }
            });
            // 最多保留最近 20 条（remove(0) 保持引用不变，避免破坏 lambda 的 effectively-final 约束）
            while (existing.size() > 20) {
                existing.remove(0);
            }
            caseProfile.setLawsJson(existing);
            log.info("会话 {} 引用法条已合并: {} 条", caseProfile.getCaseId(), existing.size());
        } catch (Exception e) {
            log.warn("引用法条落库失败: {}", e.getMessage());
        }
    }

    private void saveMessage(Long sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }
}