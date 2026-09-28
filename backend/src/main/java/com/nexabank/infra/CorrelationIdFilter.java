package com.nexabank.infra;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlation-ID handling for every HTTP request.
 *
 * <ul>
 *   <li>Client-sent {@code X-Correlation-Id} is accepted only when it matches
 *   {@code [A-Za-z0-9_-]{1,64}}; anything else (missing, blank, too long,
 *   illegal characters) is replaced by a generated UUID.</li>
 *   <li>The id is exposed as a request attribute, echoed back as the
 *   {@code X-Correlation-Id} response header, and placed in the MDC under
 *   {@code correlationId} so every log line in the request carries it.</li>
 *   <li>MDC is always cleaned up after the request.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".ID";

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = normalize(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, correlationId);
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    static String normalize(String sent) {
        if (sent != null) {
            String trimmed = sent.trim();
            if (VALID.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }

    /** Current request's correlation id, or {@code "none"} outside a request. */
    public static String current() {
        String id = MDC.get(MDC_KEY);
        return id == null ? "none" : id;
    }
}
