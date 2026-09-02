package com.umang.notification.model.entity;

import com.umang.notification.model.enums.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A reusable message template with {@code {{placeholder}}} tokens. The
 * {@code TemplateService} renderer substitutes request params into {@link #subject}
 * (channel-specific — SMS/PUSH may ignore it) and {@link #body}.
 *
 * <p>{@code templateKey} is the stable business identifier callers reference
 * (e.g. {@code "welcome"}); one key can have a row per {@link Channel}.
 */
@Entity
@Table(name = "templates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Template extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_key", nullable = false)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private Channel channel;

    @Column(name = "subject")
    private String subject;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;
}
