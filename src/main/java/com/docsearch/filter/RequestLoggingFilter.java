package com.docsearch.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Generates a correlation ID for every request and logs method, path,
 * tenant, status, and duration. In production, this ID propagates
 * through Kafka message headers for end-to-end tracing.
 */
@Component
@Order(0)
@Slf4j
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpRes = (HttpServletResponse) res;

        String corrId = httpReq.getHeader("X-Correlation-Id");
        if (corrId == null || corrId.isBlank()) {
            corrId = UUID.randomUUID().toString().substring(0, 8);
        }

        MDC.put("correlationId", corrId);
        httpRes.setHeader("X-Correlation-Id", corrId);

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            log.info("[{}] {} {} tenant={} status={} took={}ms",
                    corrId, httpReq.getMethod(), httpReq.getRequestURI(),
                    httpReq.getHeader("X-Tenant-Id"), httpRes.getStatus(),
                    System.currentTimeMillis() - start);
            MDC.clear();
        }
    }
}