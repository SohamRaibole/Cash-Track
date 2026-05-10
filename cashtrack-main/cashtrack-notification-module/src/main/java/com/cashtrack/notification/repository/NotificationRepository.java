package com.cashtrack.notification.repository;

import com.cashtrack.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByAccountId(String accountId);
}