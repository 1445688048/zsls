// CaseService.java
// 案件服务层，封装案件 CRUD 业务逻辑
package com.palmlawyer.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.palmlawyer.entity.CaseProfile;
import com.palmlawyer.mapper.CaseProfileMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 案件服务层
 *
 * <p>提供案件的创建、查询、更新等操作。
 * <p>开发阶段使用 MyBatis-Plus，上线后切换数据库即可。
 */
@Service
public class CaseService {

    /** LambdaUpdateWrapper 更新 JSON 列时需显式指定 TypeHandler */
    private static final String JSON_TYPE_HANDLER =
            "typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler";

    private final CaseProfileMapper mapper;

    public CaseService(CaseProfileMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 创建新案件
     *
     * @param userId     用户 ID
     * @param domainType 领域类型
     * @param title      案件标题
     * @return 创建后的案件对象
     */
    public CaseProfile create(Long userId, String domainType, String title, String description) {
        CaseProfile c = new CaseProfile();
        c.setUserId(userId);
        c.setDomainType(domainType != null ? domainType : "LABOR");
        c.setTitle(title != null ? title : c.getDomainType() + "案件");
        c.setStatus("COLLECTING");
        Map<String, Object> facts = new HashMap<>();
        if (description != null && !description.isBlank()) {
            facts.put("_description", description);
        }
        c.setFactsJson(facts);
        c.setIssuesJson(new ArrayList<>());
        c.setLawsJson(new ArrayList<>());
        c.setTimelineJson(new ArrayList<>());
        c.setEvidenceRefs(new ArrayList<>());
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        mapper.insert(c);
        return c;
    }

    /**
     * 查询用户的所有案件
     *
     * @param userId 用户 ID
     * @return 案件列表，按创建时间倒序
     */
    public List<CaseProfile> list(Long userId) {
        return mapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CaseProfile>()
                .eq(CaseProfile::getUserId, userId)
                .orderByDesc(CaseProfile::getCreatedAt)
        );
    }

    /**
     * 查询单个案件
     *
     * @param caseId 案件 ID
     * @return 案件对象，不存在返回 null
     */
    public CaseProfile get(Long caseId) {
        return mapper.selectById(caseId);
    }

    /**
     * 更新案件字段（白名单制，且只更新出现的列）
     *
     * <p>status 由状态机（CaseStatusService）管理、evidenceRefs/lawsJson 由系统流程回写，
     * 均不接受客户端直接修改，避免绕过状态机或覆盖系统数据。
     *
     * @param caseId  案件 ID
     * @param updates 待更新的字段 Map
     * @return 更新后的案件对象
     */
    public CaseProfile update(Long caseId, Map<String, Object> updates) {
        CaseProfile c = mapper.selectById(caseId);
        if (c == null) {
            throw new IllegalArgumentException("案件不存在: " + caseId);
        }
        LambdaUpdateWrapper<CaseProfile> uw = new LambdaUpdateWrapper<CaseProfile>()
                .eq(CaseProfile::getCaseId, caseId);
        boolean changed = false;
        if (updates.containsKey("title")) {
            Object title = updates.get("title");
            uw.set(CaseProfile::getTitle, title == null ? null : String.valueOf(title));
            changed = true;
        }
        if (updates.containsKey("factsJson") && updates.get("factsJson") instanceof Map) {
            uw.set(CaseProfile::getFactsJson, updates.get("factsJson"), JSON_TYPE_HANDLER);
            changed = true;
        }
        if (updates.containsKey("issuesJson") && updates.get("issuesJson") instanceof List) {
            uw.set(CaseProfile::getIssuesJson, updates.get("issuesJson"), JSON_TYPE_HANDLER);
            changed = true;
        }
        if (!changed) {
            return c;
        }
        uw.set(CaseProfile::getUpdatedAt, LocalDateTime.now());
        mapper.update(null, uw);
        return mapper.selectById(caseId);
    }
}
