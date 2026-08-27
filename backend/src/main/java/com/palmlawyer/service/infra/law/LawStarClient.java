package com.palmlawyer.service.infra.law;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palmlawyer.service.infra.law.dto.LawStarResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 法律之星 API 客户端
 *
 * <p>真实调用远程法规检索接口；失败时返回空列表（由上层显式降级为 mock 并标记来源）。
 * <p>注意：接口契约以供应商文档为准，若响应字段与 parseResults 不符请按实际文档调整。
 */
@Slf4j
@Component
public class LawStarClient {

    @Value("${palmlawyer.law-star.api-key}")
    private String apiKey;

    @Value("${palmlawyer.law-star.base-url}")
    private String baseUrl;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public LawStarResponse semanticSearch(String query, String region) {
        LawStarResponse result = new LawStarResponse();
        result.setQuery(query);
        result.setSource("lawstar");
        try {
            String uri = baseUrl + "/law/search?keyword=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&region=" + URLEncoder.encode(region != null ? region : "全国", StandardCharsets.UTF_8);
            JsonNode node = restClient.get().uri(uri)
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve().body(JsonNode.class);
            result.setResults(parseResults(node));
            result.setTotal(result.getResults().size());
            if (result.getResults().isEmpty()) {
                log.warn("法律之星搜索无结果: {}", query);
            }
        } catch (Exception e) {
            log.warn("法律之星搜索失败(返回空，将由上层降级): {}", e.getMessage());
            result.setResults(new ArrayList<>());
        }
        return result;
    }

    public LawStarResponse.ArticleResult getArticle(String lawTitle, String articleNo) {
        // 具体法条接口契约待供应商文档确认，先返回占位（上层不会缓存占位结果）
        LawStarResponse.ArticleResult r = new LawStarResponse.ArticleResult();
        r.setLawTitle(lawTitle);
        r.setArticle("第" + articleNo + "条");
        r.setContent("法条内容获取中");
        r.setRelevanceScore(0.0);
        return r;
    }

    /**
     * 兼容多种常见响应形状解析搜索结果
     */
    private List<LawStarResponse.ArticleResult> parseResults(JsonNode node) {
        List<LawStarResponse.ArticleResult> list = new ArrayList<>();
        if (node == null) return list;
        JsonNode arr = node.has("results") ? node.get("results")
                : node.isArray() ? node : null;
        if (arr == null || !arr.isArray()) return list;
        for (JsonNode item : arr) {
            LawStarResponse.ArticleResult r = new LawStarResponse.ArticleResult();
            r.setLawTitle(text(item, "law_title", "lawTitle", "title"));
            r.setArticle(text(item, "article", "article_no", "articleNo"));
            r.setContent(text(item, "content", "article_content", "text"));
            r.setRelevanceScore(item.has("relevance_score") ? item.get("relevance_score").asDouble(0) : 0);
            list.add(r);
        }
        return list;
    }

    private String text(JsonNode item, String... keys) {
        for (String k : keys) {
            if (item.hasNonNull(k)) return item.get(k).asText();
        }
        return "";
    }
}