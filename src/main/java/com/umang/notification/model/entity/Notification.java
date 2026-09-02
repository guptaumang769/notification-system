package com.umang.notification.model.entity;

import com.umang.notification.model.enums.Channel;
import com.umang.notification.model.enums.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The durable record of a single per-channel delivery attempt. One inbound
 * {@code NotificationRequest} fans out to one {@code Notification} row per requested
 * channel, each tracking its own {@link NotificationStatus} lifecycle.
 *
 * <p>{@code idempotencyKey} carries a unique DB constraint (see Flyway V1) as the
 * durable backstop to the Redis SETNX dedupe — together they give the effectively-once
 * guarantee described in the README.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "event_key", nullable = false)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private Channel channel;

    @Column(name = "template_key", nullable = false)
    private String templateKey;

    /** JSON-serialized template params; kept so a SCHEDULED row can be rendered later. */
    @Column(name = "params_json", length = 2000)
    private String paramsJson;

    @Column(name = "rendered_subject")
    private String renderedSubject;

    @Column(name = "rendered_body", length = 2000)
    private String renderedBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    /** Unique per inbound request+channel; enforces at-most-once persistence. */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    /** Future delivery time; null means "send now". */
    @Column(name = "send_at")
    private Instant sendAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}
