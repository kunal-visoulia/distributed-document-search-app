package com.docsearch.filter;

import com.docsearch.dto.ApiResponse;
import com.docsearch.dto.ApiResponse.ApiError;
import com.docsearch.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@Order(1)
@RequiredArgsConstructor
public class TenantFilter implements Filter {

    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String TENANT_ATTR = "tenantId";

    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpRes = (HttpServletResponse) res;
        String path = httpReq.getRequestURI();

        if (path.startsWith("/api/v1/health") || path.startsWith("/actuator")
                || path.startsWith("/swagger") || path.startsWith("/api-docs")
                || path.startsWith("/v3/api-docs")) {
            chain.doFilter(req, res);
            return;
        }

        if (path.startsWith("/api/")) {
            String tenantId = httpReq.getHeader(TENANT_HEADER);
            if (tenantId == null || tenantId.isBlank()) {
                sendError(httpRes, 400, "Missing required header: X-Tenant-Id");
                return;
            }
            if (!tenantId.matches("^[a-zA-Z0-9_-]{1,100}$")) {
                sendError(httpRes, 400, "Invalid X-Tenant-Id format");
                return;
            }
            httpReq.setAttribute(TENANT_ATTR, tenantId);
        }
        chain.doFilter(req, res);
    }

    private void sendError(HttpServletResponse res, int status, String msg) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        ApiResponse<?> response = ApiResponse.failure(msg, List.of(
                ApiError.builder()
                        .code(ErrorCode.INVALID_TENANT.getCode())
                        .description(ErrorCode.INVALID_TENANT.getDescription())
                        .build()));
        res.getWriter().write(objectMapper.writeValueAsString(response));
    }
}