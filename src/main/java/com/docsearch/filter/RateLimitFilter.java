package com.docsearch.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-tenant rate limiting using token bucket algorithm.
 * Prototype: in-memory buckets (single instance).
 * Production: Kong rate-limiting plugin backed by Redis.
 */
@Component
@Order(2)
@Slf4j
public class RateLimitFilter implements Filter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    @Value("${ratelimit.requests-per-second:100}")
    private int rps;
    @Value("${ratelimit.burst-capacity:200}")
    private int burst;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpRes = (HttpServletResponse) res;
        String tenantId = (String) httpReq.getAttribute(TenantFilter.TENANT_ATTR);

        if (tenantId == null) {
            chain.doFilter(req, res);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(tenantId, k ->
                Bucket.builder().addLimit(
                        Bandwidth.classic(burst, Refill.greedy(rps, Duration.ofSeconds(1)))
                ).build());

        if (bucket.tryConsume(1)) {
            httpRes.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            chain.doFilter(req, res);
        } else {
            log.warn("Rate limit exceeded: tenant={}", tenantId);
            httpRes.setStatus(429);
            httpRes.setContentType("application/json");
            httpRes.setHeader("Retry-After", "1");
            httpRes.getWriter().write("{\"error\": \"Rate limit exceeded\", \"retryAfter\": 1}");
        }
    }
}