package com.mappo.controlplane;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestPropertySource(properties = {
    "mappo.redis.enabled=true"
})
abstract class PostgresRedisIntegrationTestBase extends PostgresIntegrationTestBase {

    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    static {
        redis.start();
    }

    @DynamicPropertySource
    static void registerRedisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("mappo.redis.enabled", () -> "true");
        registry.add("MAPPO_REDIS_ENABLED", () -> "true");
    }
}
