package com.umang.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on JPA auditing so {@code @CreatedDate}/{@code @LastModifiedDate}
 * on {@link com.umang.notification.model.entity.BaseEntity} are populated.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
