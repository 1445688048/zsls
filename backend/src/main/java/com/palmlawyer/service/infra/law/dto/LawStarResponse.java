package com.palmlawyer.service.infra.law.dto;

import lombok.Data;
import java.util.List;

@Data
public class LawStarResponse {
    private List<ArticleResult> results;
    private int total;
    private String query;
    /** 数据来源: lawstar(真实检索) / mock(示例数据) */
    private String source;

    @Data
    public static class ArticleResult {
        private String lawTitle;
        private String article;
        private String content;
        private double relevanceScore;
    }
}