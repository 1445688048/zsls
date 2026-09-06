// ChatController.java
// 对话控制器，使用 Spring AI 2.0 实现 SSE 流式对话（会话属主校验）
package com.palmlawyer.controller;

import com.palmlawyer.entity.ChatMessage;
import com.palmlawyer.entity.ChatSession;
import com.palmlawyer.service.CaseGuard;
import com.palmlawyer.service.ChatService;
import com.palmlawyer.util.AuthContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 对话控制器
 * 
 * <p>提供 SSE 流式对话接口，使用 Spring AI 2.0 实现。
 */
@Slf4j
@RestController
@RequestMapping("/chat/sessions")
public class ChatController {

    private final ChatService chatService;
    private final CaseGuard caseGuard;

    public ChatController(ChatService chatService, CaseGuard caseGuard) {
        this.chatService = chatService;
        this.caseGuard = caseGuard;
    }

    /**
     * 创建新会话（校验案件属主）
     */
    @PostMapping
    public ChatSession createSession(@RequestBody Map<String, Object> body) {
        Object rawCaseId = body.get("caseId");
        if (rawCaseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 caseId");
        }
        Long caseId = Long.valueOf(rawCaseId.toString());
        Long userId = AuthContext.userId();
        caseGuard.assertOwner(caseId, userId);
        log.info("创建新会话: caseId={}, userId={}", caseId, userId);
        return chatService.createSession(caseId, userId);
    }

    /**
     * 发送消息（SSE 流式响应）
     */
    @PostMapping(value = "/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> sendMessage(
        @PathVariable Long sessionId,
        @RequestBody Map<String, Object> body
    ) {
        assertSessionOwner(sessionId);
        String content = requireContent(body);
        log.info("收到消息: sessionId={}", sessionId);
        return chatService.chatStream(sessionId, content);
    }

    /**
     * 发送消息（非流式响应）
     */
    @PostMapping("/{sessionId}/messages/sync")
    public Map<String, String> sendMessageSync(
        @PathVariable Long sessionId,
        @RequestBody Map<String, Object> body
    ) {
        assertSessionOwner(sessionId);
        String content = requireContent(body);
        String response = chatService.chat(sessionId, content);
        return Map.of("sessionId", sessionId.toString(), "response", response);
    }

    /**
     * 查询会话消息列表
     */
    @GetMapping("/{sessionId}/messages")
    public List<ChatMessage> listMessages(@PathVariable Long sessionId) {
        assertSessionOwner(sessionId);
        return chatService.getSessionMessages(sessionId);
    }

    /**
     * 清除会话历史
     */
    @DeleteMapping("/{sessionId}/history")
    public Map<String, String> clearHistory(@PathVariable Long sessionId) {
        assertSessionOwner(sessionId);
        chatService.clearSessionHistory(sessionId);
        return Map.of("message", "会话历史已清除");
    }

    /**
     * 会话属主校验：会话不存在或不属于当前用户 → 404
     */
    private void assertSessionOwner(Long sessionId) {
        ChatSession s = chatService.getSession(sessionId);
        if (s == null || s.getCaseId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        caseGuard.assertOwner(s.getCaseId(), AuthContext.userId());
    }

    /** 消息内容校验：缺失/空白 → 400（数据库 content 列非空，放行会变成 500） */
    private String requireContent(Map<String, Object> body) {
        Object raw = body.get("content");
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息内容不能为空");
        }
        return String.valueOf(raw);
    }
}
