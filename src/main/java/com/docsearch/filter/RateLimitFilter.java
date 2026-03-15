package com.docsearch.filter;

import com.docsearch.dto.ApiResponse;
import com.docsearch.dto.ApiResponse.ApiError;
import com.docsearch.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(2)
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter implements Filter {

    @Value("${ratelimit.requests-per-second:100}")
    private int rps;

    @Value("${ratelimit.burst-capacity:200}")
    private int burst;

    private final ObjectMapper objectMapper;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

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
            ApiResponse<?> response = ApiResponse.failure("Rate limit exceeded", List.of(
                    ApiError.builder()
                            .code(ErrorCode.RATE_LIMIT_EXCEEDED.getCode())
                            .description(ErrorCode.RATE_LIMIT_EXCEEDED.getDescription())
                            .build()));
            httpRes.getWriter().write(objectMapper.writeValueAsString(response));
        }
    }
}