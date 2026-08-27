// CaseController.java
// 案件控制器，处理案件的 HTTP 请求（当前登录用户 + 属主校验）
package com.palmlawyer.controller;

import com.palmlawyer.entity.CaseProfile;
import com.palmlawyer.service.CaseGuard;
import com.palmlawyer.service.CaseService;
import com.palmlawyer.util.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 案件控制器
 *
 * <p>提供案件的 CRUD 接口，所有业务逻辑委托给 CaseService。
 */
@RestController
@RequestMapping("/cases")
public class CaseController {

    private final CaseService caseService;
    private final CaseGuard caseGuard;

    public CaseController(CaseService caseService, CaseGuard caseGuard) {
        this.caseService = caseService;
        this.caseGuard = caseGuard;
    }

    /**
     * 创建新案件
     */
    @PostMapping
    public CaseProfile create(@RequestBody Map<String, Object> body) {
        return caseService.create(AuthContext.userId(),
                (String) body.getOrDefault("domainType", "LABOR"),
                (String) body.getOrDefault("title", "案件"),
                (String) body.getOrDefault("description", null));
    }

    /**
     * 查询当前用户案件列表
     */
    @GetMapping
    public List<CaseProfile> list() {
        return caseService.list(AuthContext.userId());
    }

    /**
     * 查询单个案件（属主校验）
     */
    @GetMapping("/{caseId}")
    public CaseProfile get(@PathVariable Long caseId) {
        caseGuard.assertOwner(caseId, AuthContext.userId());
        return caseService.get(caseId);
    }

    /**
     * 更新案件（属主校验）
     */
    @PutMapping("/{caseId}")
    public CaseProfile update(@PathVariable Long caseId, @RequestBody Map<String, Object> body) {
        caseGuard.assertOwner(caseId, AuthContext.userId());
        return caseService.update(caseId, body);
    }
}
