package com.nexabank.infra;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Rate-limit policy evaluation.
 *
 * <p>Rule selection: longest matching {@code pathPrefix} from
 * {@code app.rate-limit.rules wins}; otherwise the configured default
 * applies. Only paths covered by a rule or eligible for the default money/
 * auth surface are evaluated — internal calls bypass the filter
 * entirely.</p>
 *
 * <p>Key strategy: {@code <prefix>|user:<username>} for authenticated
 * callers (isolates users behind shared NAT), otherwise
 * {@code <prefix>|ip:<client-ip>} (first {@code X-Forwarded-For} entry or
 * remote address). Keys contain no secrets.</p>
 */
@Service
public class RateLimitService {

    private final InfraProperties properties;
    private final RateLimitStore store;

    public RateLimitService(InfraProperties properties, RateLimitStore store) {
        this.properties = properties;
        this.store = store;
    }

    /**
     * Evaluates the quota for this request. No-op when disabled.
     *
     * @throws RateLimitExceededException when the window is exhausted
     */
    public void check(HttpServletRequest request) {
        if (!properties.getRateLimit().isEnabled()) {
            return;
        }
        String path = request.getRequestURI();
        Rule rule = match(path);
        if (rule == null) {
            return;
        }
        String key = rule.pathPrefix() + "|" + identity(request);
        RateLimitStore.Acquisition acquisition =
                store.tryAcquire(key, rule.maxRequests(), rule.window());
        if (!acquisition.allowed()) {
            throw new RateLimitExceededException(
                    "Too many requests. Please try again shortly.", acquisition.retryAfterSeconds());
        }
    }

    /** Visible for tests: longest-prefix rule match, or null when none matches. */
    Rule match(String path) {
        InfraProperties.RateLimit config = properties.getRateLimit();
        InfraProperties.RateLimit.Rule best = null;
        for (InfraProperties.RateLimit.Rule candidate : config.getRules()) {
            if (path.startsWith(candidate.getPathPrefix())
                    && (best == null
                            || candidate.getPathPrefix().length() > best.getPathPrefix().length())) {
                best = candidate;
            }
        }
        if (best != null) {
            return new Rule(best.getPathPrefix(), best.getMaxRequests(), best.getWindow());
        }
        return null;
    }

    private String identity(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return "user:" + authentication.getName();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        return "ip:" + (ip == null || ip.isBlank() ? "unknown" : ip);
    }

    /** Resolved quota for one request path. */
    public record Rule(String pathPrefix, int maxRequests, Duration window) {
    }
}
