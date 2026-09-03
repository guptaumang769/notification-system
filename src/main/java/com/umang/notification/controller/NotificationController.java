package com.umang.notification.controller;

import com.umang.notification.dto.request.NotificationRequest;
import com.umang.notification.dto.response.NotificationAccepted;
import com.umang.notification.dto.response.NotificationView;
import com.umang.notification.service.NotificationIngestionService;
import com.umang.notification.service.NotificationQueryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public API for the notification platform.
 *
 * <ul>
 *   <li>{@code POST /api/v1/notifications} — pub/sub ingestion; validates + publishes,
 *       returns 202 Accepted (fire-and-forget).</li>
 *   <li>{@code GET /api/v1/notifications/{id}} — terminal status of one notification.</li>
 *   <li>{@code GET /api/v1/notifications/user/{userId}} — keyset-paginated history.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationIngestionService ingestionService;
    private final NotificationQueryService queryService;

    @PostMapping
    public ResponseEntity<NotificationAccepted> submit(@Valid @RequestBody NotificationRequest request) {
        NotificationAccepted accepted = ingestionService.publish(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted);
    }

    @GetMapping("/{id}")
    public NotificationView getById(@PathVariable Long id) {
        return queryService.getById(id);
    }

    @GetMapping("/user/{userId}")
    public List<NotificationView> getUserHistory(
            @PathVariable String userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.getUserHistory(userId, cursor, size);
    }
}
