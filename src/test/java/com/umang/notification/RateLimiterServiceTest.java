package com.umang.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umang.notification.service.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** The fixed-window counter allows up to the cap, then trips. */
@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Test
    void allowsUpToCapThenTrips() {
        RateLimiterService limiter = new RateLimiterService(redisTemplate, 3, 60);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Simulate the counter climbing 1,2,3,4 across four sends.
        when(valueOps.increment(anyString())).thenReturn(1L, 2L, 3L, 4L);

        assertThat(limiter.tryConsume("user-1")).isTrue();   // 1
        assertThat(limiter.tryConsume("user-1")).isTrue();   // 2
        assertThat(limiter.tryConsume("user-1")).isTrue();   // 3 (== cap)
        assertThat(limiter.tryConsume("user-1")).isFalse();  // 4 → tripped
    }

    @Test
    void setsTtlOnFirstHitOfWindow() {
        RateLimiterService limiter = new RateLimiterService(redisTemplate, 5, 60);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        limiter.tryConsume("user-1");

        // First INCR in the window must start the TTL so the window auto-resets.
        verify(redisTemplate).expire(anyString(), any());
    }
}
