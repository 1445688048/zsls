// ChatService.java
// 对话服务层：Agent 编排（系统提示词 + DB 历史窗口 + 免责声明尾帧 + 事实回写）
package com.palmlawyer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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

    /** LambdaUpdateWrapper 更新 JSON 列时需显式指定 TypeHandler（实体上的 @TableField 注解只在整实体模式生效） */
    private static final String JSON_TYPE_HANDLER =
            "typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler";

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
        return chatStreamInternal(sessionId, userMessage, null);
    }

    private Flux<String> chatStreamInternal(Long sessionId, String userMessage, CompletableFuture<Void> persisted) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        CaseProfile caseProfile = caseMapper.selectById(session.getCaseId());
        if (caseProfile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "案件不存在");
        }

        saveMessage(sessionId, "user", userMessage);

        // 1) 系统提示词（角色 + 案件事实 + 证据收集状态 + 领域 Schema + 本题参考法条）
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
            .doOnComplete(() -> {
                String snapshot = full.toString();
                // 落库包含阻塞 JDBC/HTTP，不能在 Reactor/Netty 事件循环线程上执行，调度到弹性线程池
                Schedulers.boundedElastic().schedule(() -> {
                    try {
                        afterAnswer(sessionId, caseProfile, userMessage, snapshot);
                    } finally {
                        if (persisted != null) persisted.complete(null);
                    }
                });
            });
    }

    /**
     * 非流式对话（测试/兜底场景），复用同一编排；等待落库完成后再返回最新助手消息
     */
    public String chat(Long sessionId, String userMessage) {
        CompletableFuture<Void> persisted = new CompletableFuture<>();
        chatStreamInternal(sessionId, userMessage, persisted).blockLast();
        try {
            persisted.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
        // 落库完成后重新读取助手消息
        List<ChatMessage> msgs = messageMapper.selectBySessionId(sessionId);
        StringBuilder full = new StringBuilder();
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
     * 构建系统提示词：领域 Schema + 证据收集状态 + 本题参考法条（top3）
     */
    private String buildSystemPrompt(CaseProfile caseProfile, String userMessage) {
        StringBuilder sb = new StringBuilder(promptBuilder.build(caseProfile));

        // 注入真实证据收集状态，使模型的"证据建议"与实际上传/勾选一致
        try {
            EvidenceAdvisor.EvidenceAdvice advice =
                    evidenceAdvisor.advise(new AgentState(caseProfile.getDomainType(), caseProfile.getFactsJson()),
                            collectedEvidenceIds(caseProfile));
            if (advice.hasMissingNecessary() || advice.hasEarlyWarnings()) {
                sb.append("\n## 证据收集状态\n");
                if (advice.hasMissingNecessary()) {
                    sb.append("仍缺失的必要证据：");
                    advice.getMissingNecessary().forEach(i -> sb.append(i.getName() != null ? i.getName() : i.getId()).append("、"));
                    sb.setLength(sb.length() - 1);
                    sb.append("。请在证据建议中优先提示用户补充。\n");
                }
                for (Map<String, String> w : advice.getEarlyWarnings()) {
                    sb.append("风险预警：").append(w.getOrDefault("warningText", "")).append("\n");
                }
            }
        } catch (Exception e) {
            log.warn("证据状态注入失败(不影响对话): {}", e.getMessage());
        }

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

    /** 提取案件已收集证据的 refId 集合（evidenceRefs 中 collected=true 的项） */
    private Set<String> collectedEvidenceIds(CaseProfile caseProfile) {
        Set<String> ids = new HashSet<>();
        List<Map<String, Object>> refs = caseProfile.getEvidenceRefs();
        if (refs == null) return ids;
        for (Map<String, Object> r : refs) {
            Object refId = r.get("refId");
            if (refId != null && Boolean.TRUE.equals(r.get("collected"))) {
                ids.add(String.valueOf(refId));
            }
        }
        return ids;
    }

    private String extractText(ChatResponse response) {
        if (response.getResult() != null && response.getResult().getOutput() != null) {
            String text = response.getResult().getOutput().getText();
            return text != null ? text : "";
        }
        return "";
    }

    /**
     * 流式完成后的落库与案件回写（各子步骤独立 try/catch，互不拖累）。
     * 全程可能运行在弹性线程池上，只做精准列更新，不整实体回写，降低并发覆盖面。
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

        Map<String, Object> mergedFacts = caseProfile.getFactsJson();
        try {
            Map<String, Object> facts = factExtractor.extract(fullResponse, caseProfile.getFactsJson());
            if (!facts.isEmpty()) {
                mergedFacts = facts;
                log.info("会话 {} 案件事实已提取: {} 个字段", sessionId, facts.size());
            }
        } catch (Exception e) {
            log.error("事实提取回写失败: sessionId={}", sessionId, e);
        }

        // 本轮引用法条合并落库（F3）
        List<Map<String, Object>> mergedLaws = caseProfile.getLawsJson();
        try {
            mergedLaws = mergeLawRefs(caseProfile, userMessage);
        } catch (Exception e) {
            log.warn("引用法条落库失败: {}", e.getMessage());
        }

        // 精准更新 facts/laws/updatedAt，避免整实体回写覆盖 evidence_refs 等并发写入的列
        final Map<String, Object> factsToSave = mergedFacts;
        final List<Map<String, Object>> lawsToSave = mergedLaws;
        try {
            LambdaUpdateWrapper<CaseProfile> uw = new LambdaUpdateWrapper<CaseProfile>()
                    .eq(CaseProfile::getCaseId, caseProfile.getCaseId())
                    .set(CaseProfile::getUpdatedAt, LocalDateTime.now());
            if (factsToSave != null && !factsToSave.isEmpty()) {
                uw.set(CaseProfile::getFactsJson, factsToSave, JSON_TYPE_HANDLER);
            }
            if (lawsToSave != null) {
                uw.set(CaseProfile::getLawsJson, lawsToSave, JSON_TYPE_HANDLER);
            }
            caseMapper.update(null, uw);
            caseStatusService.reevaluate(caseProfile.getCaseId());
        } catch (Exception e) {
            log.error("案件档案回写/状态重估失败: sessionId={}", sessionId, e);
        }

        // 证据建议（此时已含真实收集状态；缺失项已注入下一轮系统提示词）
        try {
            EvidenceAdvisor.EvidenceAdvice advice = evidenceAdvisor.advise(
                    new AgentState(caseProfile.getDomainType(), factsToSave), collectedEvidenceIds(caseProfile));
            log.info("[AGENT] 会话 {} 证据建议: 必要缺失={}, 增强缺失={}, 预警={}",
                    sessionId, advice.getMissingNecessary().size(),
                    advice.getMissingEnhancing().size(), advice.getEarlyWarnings().size());
        } catch (Exception e) {
            log.error("证据建议生成失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 将本轮检索到的引用法条去重合并进案件 lawsJson（F3：让"法律意见"有数据可展示）
     *
     * @return 合并后的完整法条列表
     */
    private List<Map<String, Object>> mergeLawRefs(CaseProfile caseProfile, String userMessage) {
        List<Map<String, Object>> existing = new ArrayList<>(
                caseProfile.getLawsJson() != null ? caseProfile.getLawsJson() : new ArrayList<>());
        try {
            List<LawStarResponse.ArticleResult> refs = lawService.search(userMessage, "全国");
            if (refs == null || refs.isEmpty()) {
                return existing;
            }
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
            log.info("会话 {} 引用法条已合并: {} 条", caseProfile.getCaseId(), existing.size());
        } catch (Exception e) {
            log.warn("引用法条检索失败，保留既有法条: {}", e.getMessage());
        }
        return existing;
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