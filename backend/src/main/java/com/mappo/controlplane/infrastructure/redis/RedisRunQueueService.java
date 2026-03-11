package com.mappo.controlplane.infrastructure.redis;

import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.execution.RunQueue;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisRunQueueService implements RunQueue {

    private final StringRedisTemplate redisTemplate;
    private final MappoProperties properties;
    private final String ownerId = "mappo-run-worker-" + UUID.randomUUID();

    @Override
    public boolean isEnabled() {
        return properties.getRedis().isEnabled();
    }

    @Override
    public void enqueue(String runId) {
        enqueue(runId, false);
    }

    @Override
    public void enqueue(String runId, boolean force) {
        if (!isEnabled()) {
            return;
        }
        String normalizedRunId = normalize(runId);
        if (normalizedRunId.isBlank()) {
            return;
        }

        String dedupKey = dedupKey(normalizedRunId);
        if (force) {
            redisTemplate.delete(dedupKey);
        }

        Boolean queued = redisTemplate.opsForValue().setIfAbsent(
            dedupKey,
            ownerId,
            Duration.ofMillis(properties.getRedis().getQueueDedupTtlMs())
        );
        if (Boolean.TRUE.equals(queued)) {
            redisTemplate.opsForList().leftPush(properties.getRedis().getQueueKey(), normalizedRunId);
        }
    }

    @Override
    public String poll() {
        if (!isEnabled()) {
            return "";
        }
        String value = redisTemplate.opsForList().rightPop(properties.getRedis().getQueueKey());
        return normalize(value);
    }

    @Override
    public boolean acquireRunLease(String runId) {
        if (!isEnabled()) {
            return true;
        }
        String normalizedRunId = normalize(runId);
        if (normalizedRunId.isBlank()) {
            return false;
        }
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
            lockKey(normalizedRunId),
            ownerId,
            Duration.ofMillis(properties.getRedis().getLockLeaseMs())
        );
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public boolean isRunLeaseHeld(String runId) {
        if (!isEnabled()) {
            return false;
        }
        String normalizedRunId = normalize(runId);
        if (normalizedRunId.isBlank()) {
            return false;
        }
        Boolean exists = redisTemplate.hasKey(lockKey(normalizedRunId));
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void renewRunLease(String runId) {
        if (!isEnabled()) {
            return;
        }
        String normalizedRunId = normalize(runId);
        if (normalizedRunId.isBlank()) {
            return;
        }
        String lockKey = lockKey(normalizedRunId);
        String currentOwner = normalize(redisTemplate.opsForValue().get(lockKey));
        if (ownerId.equals(currentOwner)) {
            redisTemplate.expire(lockKey, Duration.ofMillis(properties.getRedis().getLockLeaseMs()));
        }
    }

    @Override
    public void releaseRunLease(String runId) {
        String normalizedRunId = normalize(runId);
        if (normalizedRunId.isBlank()) {
            return;
        }
        if (isEnabled()) {
            String lockKey = lockKey(normalizedRunId);
            String currentOwner = normalize(redisTemplate.opsForValue().get(lockKey));
            if (ownerId.equals(currentOwner)) {
                redisTemplate.delete(lockKey);
            }
            redisTemplate.delete(dedupKey(normalizedRunId));
        }
    }

    private String lockKey(String runId) {
        return properties.getRedis().getQueueLockPrefix() + runId;
    }

    private String dedupKey(String runId) {
        return properties.getRedis().getQueueDedupPrefix() + runId;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
