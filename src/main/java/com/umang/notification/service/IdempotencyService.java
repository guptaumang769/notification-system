package com.umang.notification.service;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Idempotency guard backed by Redis {@code SETNX} (set-if-not-exists).
 *
 * <p>Kafka gives at-least-once delivery: a consumer can see the same event twice (rebalance,
 * retry, redelivery after a crash before the offset commits). {@code markIfFirst} claims the
 * idempotency key atomically — the first caller wins and proceeds, any duplicate is skipped.
 * An <b>at-least-once broker</b> plus an <b>idempotent consumer</b> yields <b>effectively-once</b>
 * side effects (the user gets the message exactly once) without needing exactly-once transactions.
 *
 * <p>Redis is the fast first line; the {@code notifications.idempotency_key} unique constraint
 * (Flyway V1) is the durable backstop if a key expires from Redis or Redis is flushed.
 */
@Slf4j
@Service
public class IdempotencyService {

    private static final String KEY_PREFIX = "idem:notif:";

    private final RedisTemplate<String, String> redisTemplate;
    private final Duration ttl;

    public IdempotencyService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${idempotency.ttl-hours:24}") long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofHours(ttlHours);
    }

    /**
     * Atomically claim the key. Returns true if this caller is the first to see it (proceed
     * with the send); false if it was already claimed (a duplicate — skip).
     */
    public boolean markIfFirst(String idempotencyKey) {
        Boolean set = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + idempotencyKey, "1", ttl);
        boolean first = Boolean.TRUE.equals(set);
        if (!first) {
            log.info("Duplicate suppressed for idempotency key {}", idempotencyKey);
        }
        return first;
    }

    /** Whether the key has already been claimed (used at ingestion to short-circuit). */
    public boolean alreadySeen(String idempotencyKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + idempotencyKey));
    }
}
