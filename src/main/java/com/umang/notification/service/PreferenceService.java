package com.umang.notification.service;

import com.umang.notification.model.entity.UserPreference;
import com.umang.notification.model.enums.Channel;
import com.umang.notification.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves per-user delivery preferences. The consumer calls {@link #isChannelAllowed}
 * before every send and skips the channel (records SKIPPED) when the user has opted out.
 *
 * <p>Default-allow: a user with no stored preference row is treated as opted-in on all
 * channels, so a brand-new user still gets transactional notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final UserPreferenceRepository preferenceRepository;

    public boolean isChannelAllowed(String userId, Channel channel) {
        return preferenceRepository
                .findByUserId(userId)
                .map(pref -> {
                    boolean allowed = pref.isChannelEnabled(channel);
                    // Quiet-hours policy would ALSO be evaluated here: if now() falls inside
                    // [quietHoursStart, quietHoursEnd) in the user's timezone, a production
                    // system would defer non-critical sends until quietHoursEnd rather than
                    // drop them. We store the window on UserPreference and leave the gate off
                    // to keep the demo deterministic.
                    return allowed;
                })
                .orElse(true); // no preference row ⇒ opted-in by default
    }

    public UserPreference save(UserPreference preference) {
        return preferenceRepository.save(preference);
    }
}
