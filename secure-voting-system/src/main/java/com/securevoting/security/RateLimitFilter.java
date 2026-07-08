package com.securevoting.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 13 — sliding-window per-IP+endpoint rate-limit filter.
 *
 * Each (clientIp, ruleKey) pair keeps a deque of recent request timestamps.
 * On every match the deque is pruned to the rule's window; if the remaining
 * count meets or exceeds the rule's max, the request short-circuits with
 * HTTP 429 and a Retry-After header.
 *
 * Runs BEFORE Spring Security's authentication filter so unauthenticated
 * scrapers are blocked early. X-Forwarded-For aware for reverse-proxy
 * deployments.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final Logger AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final List<Rule> RULES = List.of(
            new Rule("/login",       10, 60_000L,  "POST"),
            new Rule("/register",     5, 900_000L, "POST"),
            new Rule("/verify-otp",  10, 60_000L,  "POST"),
            new Rule("/resend-otp",   3, 60_000L,  "POST"),
            new Rule("/vote/",       30, 60_000L,  null),    // all methods
            new Rule("/chatbot/",    60, 60_000L,  "POST")
    );

    /** Bucket key = clientIp + "|" + rule.prefix. Value = monotonically growing timestamps (ms). */
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    @Override
    protected void initFilterBean() {
        log.info("Rate-limit filter initialised with {} rule(s).", RULES.size());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        Rule rule = matchRule(req.getRequestURI(), req.getMethod());
        if (rule == null) {
            chain.doFilter(req, res);
            return;
        }

        String ip = clientIp(req);
        String key = ip + "|" + rule.prefix;
        long now = System.currentTimeMillis();
        long cutoff = now - rule.windowMs;

        Deque<Long> dq = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (dq) {
            while (!dq.isEmpty() && dq.peekFirst() < cutoff) {
                dq.pollFirst();
            }
            if (dq.size() >= rule.max) {
                long retryAfter = Math.max(1L, (dq.peekFirst() + rule.windowMs - now) / 1000L);
                AUDIT.info("RATE_LIMIT_HIT ip={} method={} path={} bucket={} count={} max={}",
                        ip, req.getMethod(), req.getRequestURI(),
                        rule.prefix, dq.size(), rule.max);
                res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                res.setHeader("Retry-After", String.valueOf(retryAfter));
                res.setContentType("text/plain;charset=UTF-8");
                res.getWriter().write("Rate limit exceeded for " + rule.prefix
                        + ". Try again in " + retryAfter + " s.");
                return;
            }
            dq.addLast(now);
        }

        chain.doFilter(req, res);
    }

    private static Rule matchRule(String path, String method) {
        if (path == null) return null;
        for (Rule r : RULES) {
            if (r.method != null && !r.method.equalsIgnoreCase(method)) continue;
            if (r.prefix.endsWith("/")) {
                if (path.startsWith(r.prefix)) return r;
            } else {
                if (path.equals(r.prefix)) return r;
            }
        }
        return null;
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            String first = (comma > 0 ? xff.substring(0, comma) : xff).trim();
            if (!first.isEmpty()) return first;
        }
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return req.getRemoteAddr();
    }

    /** method == null means "any method". */
    private record Rule(String prefix, int max, long windowMs, String method) {}
}