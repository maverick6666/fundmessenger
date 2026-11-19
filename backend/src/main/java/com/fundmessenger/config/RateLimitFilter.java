package com.fundmessenger.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private static final int MAX_BUCKETS = 10_000;

    @org.springframework.beans.factory.annotation.Value("${app.environment}")
    private String environment;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // development 환경에서는 rate limit 비활성화 (E2E 테스트 지원)
        if ("development".equals(environment)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = getClientKey(request);
        String path = request.getRequestURI();
        String method = request.getMethod();

        Bucket bucket = buckets.computeIfAbsent(clientKey + ":" + getBucketKey(path, method), k -> createBucket(path, method));

        // 맵 크기 제한 (메모리 보호)
        if (buckets.size() > MAX_BUCKETS) {
            buckets.clear();
        }

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}");
        }
    }

    private Bucket createBucket(String path, String method) {
        if (path.startsWith("/api/auth/")) {
            return Bucket.builder()
                    .addLimit(Bandwidth.simple(60, Duration.ofMinutes(1)))
                    .build();
        }
        if ("POST".equals(method) && (path.contains("/files") || path.contains("/drive/files"))) {
            return Bucket.builder()
                    .addLimit(Bandwidth.simple(30, Duration.ofMinutes(1)))
                    .build();
        }
        return Bucket.builder()
                .addLimit(Bandwidth.simple(600, Duration.ofMinutes(1)))
                .build();
    }

    private String getBucketKey(String path, String method) {
        if (path.startsWith("/api/auth/")) return "auth";
        if ("POST".equals(method) && (path.contains("/files") || path.contains("/drive/files"))) return "upload";
        return "general";
    }

    /** 클라이언트 식별: JWT 토큰에서 userId 추출, 없으면 IP */
    private String getClientKey(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            // JWT payload에서 userId를 키로 사용 (토큰 파싱 없이 토큰 자체를 해시)
            return "user:" + auth.substring(7).hashCode();
        }
        return "ip:" + getClientIp(request);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
