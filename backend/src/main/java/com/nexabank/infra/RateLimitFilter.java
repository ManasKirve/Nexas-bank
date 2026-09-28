package com.nexabank.infra;

import com.nexabank.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;

/**
 * Server-side rate limiting for the configured HTTP surface (login,
 * registration, money movement, fraud reads). Runs after the correlation
 * filter so 429 responses also carry the correlation id.
 *
 * <p>Only {@code /api/**} paths are evaluated and only when a rule prefix
 * matches — actuator, docs and static traffic are never limited. Denials
 * return {@code 429} + {@code Retry-After} with the standard
 * {@link ApiError} body.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            rateLimitService.check(request);
        } catch (RateLimitExceededException ex) {
            log.warn("Rate limit exceeded: {} correlationId={}",
                    request.getRequestURI(), CorrelationIdFilter.current());
            response.setStatus(429); // HttpServletResponse has no 429 constant on this Servlet version
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
            ApiError body = new ApiError(Instant.now(), 429, "Too Many Requests",
                    ex.getMessage(), request.getRequestURI(), null, CorrelationIdFilter.current());
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }
        chain.doFilter(request, response);
    }
}
