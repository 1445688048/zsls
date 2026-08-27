// CaseService.java
// 案件服务层，封装案件 CRUD 业务逻辑
package com.palmlawyer.service;

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
     * 更新案件字段
     *
     * @param caseId    案件 ID
     * @param updates   待更新的字段 Map
     * @return 更新后的案件对象
     */
    public CaseProfile update(Long caseId, Map<String, Object> updates) {
        CaseProfile c = mapper.selectById(caseId);
        if (c == null) {
            throw new IllegalArgumentException("案件不存在: " + caseId);
        }
        if (updates.containsKey("factsJson")) c.setFactsJson((Map) updates.get("factsJson"));
        if (updates.containsKey("status")) c.setStatus((String) updates.get("status"));
        if (updates.containsKey("title")) c.setTitle((String) updates.get("title"));
        if (updates.containsKey("domainType")) c.setDomainType((String) updates.get("domainType"));
        if (updates.containsKey("issuesJson")) c.setIssuesJson((List) updates.get("issuesJson"));
        if (updates.containsKey("lawsJson")) c.setLawsJson((List) updates.get("lawsJson"));
        if (updates.containsKey("evidenceRefs")) c.setEvidenceRefs((List) updates.get("evidenceRefs"));
        c.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(c);
        return c;
    }
}
