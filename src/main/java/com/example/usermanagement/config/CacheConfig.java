package com.example.usermanagement.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Local (per-instance) cache for small, read-mostly reference data.
 * <p>
 * The manager is wrapped so that puts and evictions happen only AFTER the surrounding transaction
 * commits. Without that, a concurrent reader could re-populate the cache with stale data between an
 * eviction and the commit. The TTL bounds staleness across multiple instances; if you need exact
 * cross-instance invalidation, move these caches to Redis.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "permissions";
    public static final String GROUPS = "groups";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager delegate = new CaffeineCacheManager(ROLES, PERMISSIONS, GROUPS);
        delegate.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofMinutes(5)));
        return new TransactionAwareCacheManagerProxy(delegate);
    }
}
