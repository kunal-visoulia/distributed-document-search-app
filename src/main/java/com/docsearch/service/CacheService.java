package com.docsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Redis cache with generation-based invalidation.
 *
 * Keys:
 *   search:{tenant}:{generation}:{hash(query)}  TTL 60s
 *   doc:{tenant}:{docId}                         TTL 5min
 *   tenant_gen:{tenant}                          no TTL
 *
 * On write/delete: INCR tenant_gen → old search keys orphaned, expire via TTL.
 * O(1) invalidation. No KEYS/SCAN needed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${cache.search-results.ttl:60}")
    private int searchTtl;

    @Value("${cache.document.ttl:300}")
    private int docTtl;

    // ── Search cache ────────────────────────────────────────

    public <T> Optional<T> getSearchResult(String tenantId, String query, Class<T> type) {
        return getCached(searchKey(tenantId, query), type);
    }

    public void putSearchResult(String tenantId, String query, Object result) {
        putCached(searchKey(tenantId, query), result, Duration.ofSeconds(searchTtl));
    }

    // ── Document cache ──────────────────────────────────────

    public <T> Optional<T> getDocument(String tenantId, String docId, Class<T> type) {
        return getCached(docKey(tenantId, docId), type);
    }

    public void putDocument(String tenantId, String docId, Object doc) {
        putCached(docKey(tenantId, docId), doc, Duration.ofSeconds(docTtl));
    }

    // ── Invalidation ────────────────────────────────────────

    public void invalidateSearchCache(String tenantId) {
        try {
            Long gen = redisTemplate.opsForValue().increment(genKey(tenantId));
            log.debug("Cache generation for tenant {} → {}", tenantId, gen);
        } catch (Exception e) {
            log.warn("Failed to increment generation: {}", e.getMessage());
        }
    }

    public void invalidateDocument(String tenantId, String docId) {
        try {
            redisTemplate.delete(docKey(tenantId, docId));
        } catch (Exception e) {
            log.warn("Failed to delete doc cache: {}", e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private <T> Optional<T> getCached(String key, Class<T> type) {
        try {
            Object val = redisTemplate.opsForValue().get(key);
            if (val != null) {
                log.debug("Cache HIT: {}", key);
                return Optional.of(objectMapper.readValue(objectMapper.writeValueAsString(val), type));
            }
        } catch (Exception e) {
            log.warn("Cache read error: {}", e.getMessage());
        }
        log.debug("Cache MISS: {}", key);
        return Optional.empty();
    }

    private void putCached(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Cache write error: {}", e.getMessage());
        }
    }

    private long getGeneration(String tenantId) {
        try {
            Object gen = redisTemplate.opsForValue().get(genKey(tenantId));
            return gen != null ? Long.parseLong(gen.toString()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String searchKey(String tenantId, String query) {
        return "search:" + tenantId + ":" + getGeneration(tenantId) + ":" + hash(query);
    }

    private String docKey(String tenantId, String docId) {
        return "doc:" + tenantId + ":" + docId;
    }

    private String genKey(String tenantId) {
        return "tenant_gen:" + tenantId;
    }

    private String hash(String input) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
