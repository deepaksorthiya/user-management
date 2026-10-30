package com.example.usermanagement.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Populates createdAt / updatedAt on entities. Kept out of the main class so that
 * web-layer test slices do not need a JPA metamodel.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
