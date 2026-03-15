package com.docsearch.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Extracts X-Tenant-Id header from every request.
 *
 * Production: Kong validates JWT, extracts tenant_id from claims,
 * sets X-Tenant-Id (overwrites client value). Service trusts it.
 *
 * Prototype: Client provides header directly. Validated for format.
 */
@Component @Order(1) @Slf4j
public class TenantFilter implements Filter {

    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String TENANT_ATTR = "tenantId";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpRes = (HttpServletResponse) res;
        String path = httpReq.getRequestURI();

        if (path.startsWith("/api/v1/health") || path.startsWith("/actuator")) {
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
        res.getWriter().write("{\"error\": \"" + msg + "\"}");
    }
}
