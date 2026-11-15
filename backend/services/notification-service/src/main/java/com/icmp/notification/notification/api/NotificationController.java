package com.icmp.notification.notification.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.icmp.notification.common.security.CurrentUser;
import com.icmp.notification.notification.application.NotificationService;
import com.icmp.notification.notification.domain.Notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** The caller's own notifications. Works for claimants and staff alike. */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "My notifications, newest first")
    public PageResponse list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                             @PageableDefault(size = 20) Pageable pageable) {
        Page<Notification> page = service.list(CurrentUser.get().id(), unreadOnly, pageable);
        return new PageResponse(page.getContent().stream().map(NotificationView::from).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread notifications (polled by the header bell)")
    public Map<String, Long> unreadCount() {
        return Map.of("count", service.unreadCount(CurrentUser.get().id()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    public NotificationView markRead(@PathVariable UUID id) {
        return NotificationView.from(service.markRead(id, CurrentUser.get().id()));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all my notifications as read")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", service.markAllRead(CurrentUser.get().id()));
    }

    public record NotificationView(UUID id, String eventType, String category, UUID claimId, String claimNumber,
                                   String title, String message, Instant createdAt, Instant readAt, boolean read) {
        static NotificationView from(Notification n) {
            return new NotificationView(n.getId(), n.getEventType(), n.getCategory().name(), n.getClaimId(),
                    n.getClaimNumber(), n.getTitle(), n.getMessage(), n.getCreatedAt(), n.getReadAt(), n.isRead());
        }
    }

    public record PageResponse(List<NotificationView> content, int page, int size, long totalElements,
                               int totalPages) {
    }
}
