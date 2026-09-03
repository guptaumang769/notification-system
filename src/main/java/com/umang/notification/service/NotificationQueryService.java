package com.umang.notification.service;

import com.umang.notification.dto.response.NotificationView;
import com.umang.notification.exception.NotificationNotFoundException;
import com.umang.notification.repository.NotificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * Read-side queries for notification status and per-user history (keyset paginated).
 */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;

    public NotificationView getById(Long id) {
        return notificationRepository.findById(id)
                .map(NotificationView::from)
                .orElseThrow(() -> new NotificationNotFoundException(id));
    }

    /**
     * Keyset-paginated history, newest first. {@code cursor} is the last id seen on the
     * previous page (null/omitted ⇒ start from the newest). Returns up to {@code size} rows.
     */
    public List<NotificationView> getUserHistory(String userId, Long cursor, int size) {
        long effectiveCursor = cursor == null ? Long.MAX_VALUE : cursor;
        int limit = Math.min(size <= 0 ? 20 : size, MAX_PAGE_SIZE);
        return notificationRepository
                .findUserHistory(userId, effectiveCursor, Limit.of(limit))
                .stream()
                .map(NotificationView::from)
                .toList();
    }
}
