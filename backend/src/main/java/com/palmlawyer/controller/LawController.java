// LawController.java
// 法规查询控制器
package com.palmlawyer.controller;

import com.palmlawyer.service.LawService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 法规查询控制器
 *
 * <p>提供法规搜索接口，供前端直接调用。
 */
@RestController
@RequestMapping("/law")
public class LawController {

    private final LawService lawService;

    public LawController(LawService lawService) {
        this.lawService = lawService;
    }

    /**
     * 语义搜索法规（透传 source: lawstar/mock，前端据此显示来源标记）
     */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String query,
                                      @RequestParam(defaultValue = "全国") String region) {
        var result = lawService.searchWithSource(query, region);
        Map<String, Object> response = new HashMap<>();
        response.put("results", result.getResults());
        response.put("total", result.getResults() == null ? 0 : result.getResults().size());
        response.put("query", query);
        response.put("source", result.getSource());
        return response;
    }

    /**
     * 查询具体法条
     */
    @GetMapping("/article")
    public Map<String, Object> getArticle(@RequestParam String lawTitle,
                                          @RequestParam String articleNo) {
        var result = lawService.getArticle(lawTitle, articleNo);
        Map<String, Object> response = new HashMap<>();
        response.put("law_title", result.getLawTitle());
        response.put("article_no", result.getArticle());
        response.put("content", result.getContent());
        response.put("status", "有效");
        return response;
    }
}
