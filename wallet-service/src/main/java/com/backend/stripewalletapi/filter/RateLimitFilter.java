package com.backend.stripewalletapi.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rate-limit.max-requests:30}")
    private int maxRequests;

    @Value("${rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Stripe webhook bypasses rate limiting
        if (request.getRequestURI().contains("/stripe/webhook")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only rate-limit authenticated users
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        String userIdentifier = auth.getName();
        long windowKey = System.currentTimeMillis() / (windowSeconds * 1000L);
        String redisKey = "rate_limit:" + userIdentifier + ":" + windowKey;

        Long requestCount = redisTemplate.opsForValue().increment(redisKey);
        if (requestCount != null && requestCount == 1) {
            redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
        }

        if (requestCount != null && requestCount > maxRequests) {
            log.warn("Rate limit exceeded for user: {}", userIdentifier);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "message", "Too many requests. Limit: " + maxRequests + " per " + windowSeconds + " seconds",
                    "status", 429
            )));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
