// CaseStatusService.java
// 案件状态机：按事实/证据达标情况单向流转 COLLECTING -> ANALYZING -> READY
package com.palmlawyer.service;

import com.palmlawyer.entity.CaseProfile;
import com.palmlawyer.mapper.CaseProfileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CaseStatusService {

    /** 事实要素 >= 该值进入 ANALYZING（不含 _ 开头的内部字段） */
    private static final int FACTS_ANALYZING = 3;
    /** 已上传证据(有 url) >= 该值进入 READY */
    private static final int EVIDENCE_READY = 3;

    private final CaseProfileMapper caseMapper;

    public CaseStatusService(CaseProfileMapper caseMapper) {
        this.caseMapper = caseMapper;
    }

    /**
     * 重新评估案件状态（只升不降）：
     * <ul>
     *   <li>已上传证据 >= 3 -> READY</li>
     *   <li>事实要素 >= 3 -> ANALYZING</li>
     *   <li>否则保持原状</li>
     * </ul>
     */
    public void reevaluate(Long caseId) {
        CaseProfile c = caseMapper.selectById(caseId);
        if (c == null) return;

        Map<String, Object> facts = c.getFactsJson();
        long factCount = facts == null ? 0 : facts.keySet().stream()
                .filter(k -> !k.startsWith("_"))
                .count();
        List<Map<String, Object>> refs = c.getEvidenceRefs();
        long evidenceCount = refs == null ? 0 : refs.stream()
                .filter(r -> r.get("url") != null)
                .count();

        String next = null;
        if (evidenceCount >= EVIDENCE_READY) {
            next = "READY";
        } else if (factCount >= FACTS_ANALYZING) {
            next = "ANALYZING";
        }

        if (next != null && rank(next) > rank(c.getStatus())) {
            c.setStatus(next);
            c.setUpdatedAt(LocalDateTime.now());
            caseMapper.updateById(c);
            log.info("案件 {} 状态流转: {} -> {} (facts={}, evidence={})",
                    caseId, c.getStatus(), next, factCount, evidenceCount);
        }
    }

    private int rank(String s) {
        if ("READY".equals(s)) return 3;
        if ("ANALYZING".equals(s)) return 2;
        return 1; // COLLECTING / 未知
    }
}