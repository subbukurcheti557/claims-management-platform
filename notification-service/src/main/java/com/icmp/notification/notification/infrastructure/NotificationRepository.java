package com.icmp.notification.notification.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.icmp.notification.notification.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipient, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID recipient, Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(UUID recipient);

    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipient);

    long countByEventId(UUID eventId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipientUserId = :recipient AND n.readAt IS NULL")
    int markAllRead(@Param("recipient") UUID recipient, @Param("now") Instant now);
}
