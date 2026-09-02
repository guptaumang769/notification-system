package com.umang.notification.service;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Per-user send rate cap backed by Redis — a fixed-window counter: the first send in a
 * window does {@code INCR} then {@code EXPIRE(window)}; subsequent sends {@code INCR} and
 * compare to the cap. When the cap is exceeded the send is rejected and recorded as
 * RATE_LIMITED, protecting a user from being flooded (and the downstream providers from
 * our own bugs / retry storms).
 *
 * <p>Redis is the natural home for this: the counter is shared across all app instances,
 * {@code INCR} is atomic, and TTL auto-expires the window with no cleanup job. A fixed
 * window can burst at the boundary; a sliding-window log or token bucket (via a Lua script)
 * smooths that out — noted as the production upgrade.
 */
@Slf4j
@Service
public class RateLimiterService {

    private static final String KEY_PREFIX = "ratelimit:user:";

    private final RedisTemplate<String, String> redisTemplate;
    private final int maxPerWindow;
    private final Duration window;

    public RateLimiterService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${rate-limit.max-per-window:5}") int maxPerWindow,
            @Value("${rate-limit.window-seconds:60}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxPerWindow = maxPerWindow;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    /**
     * Consume one token for the user. Returns true if the send is allowed, false if the
     * per-window cap has been reached.
     */
    public boolean tryConsume(String userId) {
        String key = KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // First hit in this window — start the TTL so the window auto-resets.
            redisTemplate.expire(key, window);
        }
        boolean allowed = count != null && count <= maxPerWindow;
        if (!allowed) {
            log.warn("Rate limit tripped for user {} ({} > {} per {}s)",
                    userId, count, maxPerWindow, window.toSeconds());
        }
        return allowed;
    }
}
