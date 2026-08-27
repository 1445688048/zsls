package com.palmlawyer.service.infra.law;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palmlawyer.entity.LawCache;
import com.palmlawyer.mapper.LawCacheMapper;
import com.palmlawyer.service.infra.law.dto.LawStarResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 法律之星查询缓存服务
 *
 * <p>命中 law_cache 表（键为查询哈希）则直接返回；否则调用客户端并写入缓存。
 * <p>仅缓存真实检索结果（非空），mock 降级结果不缓存。
 */
@Slf4j
@Service
public class LawStarCacheService {

    private final LawStarClient client;
    private final LawCacheMapper cacheMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${palmlawyer.law-star.cache-ttl}")
    private String cacheTtl;

    public LawStarCacheService(LawStarClient client, LawCacheMapper cacheMapper) {
        this.client = client;
        this.cacheMapper = cacheMapper;
    }

    public LawStarResponse semanticSearch(String query, String region) {
        String key = "search:" + sha256(query + "|" + (region == null ? "" : region));
        LawStarResponse cached = readCache(key, LawStarResponse.class);
        if (cached != null) {
            return cached;
        }
        LawStarResponse resp = client.semanticSearch(query, region);
        if (resp.getResults() != null && !resp.getResults().isEmpty()) {
            writeCache(key, "lawstar", resp);
        }
        return resp;
    }

    public LawStarResponse.ArticleResult getArticle(String lawTitle, String articleNo) {
        String key = "article:" + sha256(lawTitle + "|" + articleNo);
        LawStarResponse.ArticleResult cached = readCache(key, LawStarResponse.ArticleResult.class);
        if (cached != null) {
            return cached;
        }
        LawStarResponse.ArticleResult r = client.getArticle(lawTitle, articleNo);
        // 占位内容不缓存，等真实接口接入后自然启用
        if (r != null && r.getContent() != null && !r.getContent().contains("获取中")) {
            writeCache(key, "lawstar", r);
        }
        return r;
    }

    private <T> T readCache(String key, Class<T> type) {
        try {
            LawCache hit = cacheMapper.selectById(key);
            if (hit == null || isExpired(hit) || hit.getResponseJson() == null) {
                return null;
            }
            return mapper.convertValue(hit.getResponseJson(), type);
        } catch (Exception e) {
            log.warn("法律缓存读取失败: {}", e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, String source, Object payload) {
        try {
            LawCache c = new LawCache();
            c.setCacheKey(key);
            c.setSource(source);
            c.setResponseJson(mapper.convertValue(payload, Map.class));
            c.setExpiresAt(LocalDateTime.now().plus(parseTtl(cacheTtl)));
            if (cacheMapper.selectById(key) != null) {
                cacheMapper.updateById(c);
            } else {
                cacheMapper.insert(c);
            }
        } catch (Exception e) {
            log.warn("法律缓存写入失败: {}", e.getMessage());
        }
    }

    private boolean isExpired(LawCache c) {
        return c.getExpiresAt() == null || c.getExpiresAt().isBefore(LocalDateTime.now());
    }

    /** 解析 "24h"/"1d"/"30m" 形式 TTL */
    private Duration parseTtl(String expr) {
        String s = expr == null ? "24h" : expr.trim().toLowerCase();
        try {
            if (s.endsWith("d")) return Duration.ofDays(Long.parseLong(s.substring(0, s.length() - 1)));
            if (s.endsWith("h")) return Duration.ofHours(Long.parseLong(s.substring(0, s.length() - 1)));
            if (s.endsWith("m")) return Duration.ofMinutes(Long.parseLong(s.substring(0, s.length() - 1)));
            if (s.endsWith("s")) return Duration.ofSeconds(Long.parseLong(s.substring(0, s.length() - 1)));
            return Duration.ofHours(Long.parseLong(s));
        } catch (Exception e) {
            return Duration.ofHours(24);
        }
    }

    private String sha256(String input) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 失败", e);
        }
    }
}