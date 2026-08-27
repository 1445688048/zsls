// LawService.java
// 法律 API 服务层，封装法规检索逻辑（真实检索 + 显式 mock 降级）
package com.palmlawyer.service;

import com.palmlawyer.service.infra.law.LawStarCacheService;
import com.palmlawyer.service.infra.law.dto.LawStarResponse;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LawService {

    private final LawStarCacheService cacheService;

    public LawService(LawStarCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * 语义搜索相关法规（Agent 内部使用，返回法条列表）
     */
    public List<LawStarResponse.ArticleResult> search(String query, String region) {
        LawStarResponse result = searchWithSource(query, region);
        return result.getResults() != null ? result.getResults() : new ArrayList<>();
    }

    /**
     * 语义搜索（带来源标记：lawstar / mock），供 Controller 透传前端展示
     */
    public LawStarResponse searchWithSource(String query, String region) {
        LawStarResponse result = cacheService.semanticSearch(query, region);
        if (result.getResults() == null || result.getResults().isEmpty()) {
            result.setResults(getMockResults(query));
            result.setSource("mock");
        }
        return result;
    }

    /**
     * 查询具体法条
     */
    public LawStarResponse.ArticleResult getArticle(String lawTitle, String articleNo) {
        return cacheService.getArticle(lawTitle, articleNo);
    }

    // ========== 开发阶段模拟数据（带 mock 标记，不冒充真实检索） ==========

    private List<LawStarResponse.ArticleResult> getMockResults(String query) {
        List<LawStarResponse.ArticleResult> results = new ArrayList<>();
        if (query != null && (query.contains("解除") || query.contains("辞退"))) {
            results.add(mockArticle("中华人民共和国劳动合同法", "87",
                "用人单位违反本法规定解除或者终止劳动合同的，应当依照本法第四十七条规定的经济补偿标准的二倍向劳动者支付赔偿金。"));
            results.add(mockArticle("中华人民共和国劳动合同法", "47",
                "经济补偿按劳动者在本单位工作的年限，每满一年支付一个月工资的标准向劳动者支付。"));
        }
        if (results.isEmpty()) {
            results.add(mockArticle("中华人民共和国劳动合同法", "1", "为了完善劳动合同制度，明确劳动合同双方当事人的权利和义务，保护劳动者的合法权益，构建和发展和谐稳定的劳动关系，制定本法。"));
        }
        return results;
    }

    private LawStarResponse.ArticleResult mockArticle(String title, String no, String content) {
        LawStarResponse.ArticleResult r = new LawStarResponse.ArticleResult();
        r.setLawTitle(title);
        r.setArticle("第" + no + "条");
        r.setContent(content);
        r.setRelevanceScore(0.95);
        return r;
    }
}