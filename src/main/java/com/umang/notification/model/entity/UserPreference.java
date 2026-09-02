package com.umang.notification.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.umang.notification.model.enums.Channel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-user delivery preferences: which channels the user has opted into, plus an
 * optional quiet-hours window. The consumer skips any channel the user has opted out of.
 *
 * <p>Quiet hours are stored as {@code [quietHoursStart, quietHoursEnd)} UTC hours
 * (0-23). A production system would suppress or defer non-critical sends inside this
 * window (buffer and release at {@code quietHoursEnd}, honouring the user's timezone);
 * here we model the data and comment the policy rather than gate delivery, to keep the
 * demo deterministic.
 */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreference extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    /** Start of the quiet-hours window (UTC hour 0-23), or null if none. */
    @Column(name = "quiet_hours_start")
    private Integer quietHoursStart;

    /** End of the quiet-hours window (UTC hour 0-23), or null if none. */
    @Column(name = "quiet_hours_end")
    private Integer quietHoursEnd;

    /** Whether the given channel is opted-in for this user. */
    public boolean isChannelEnabled(Channel channel) {
        return switch (channel) {
            case EMAIL -> emailEnabled;
            case SMS -> smsEnabled;
            case PUSH -> pushEnabled;
        };
    }
}
