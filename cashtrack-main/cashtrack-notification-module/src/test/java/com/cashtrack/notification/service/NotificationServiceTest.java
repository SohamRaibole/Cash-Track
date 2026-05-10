package com.cashtrack.notification.service;

import com.cashtrack.api.*;
import com.cashtrack.notification.entity.Notification;
import com.cashtrack.notification.repository.NotificationRepository;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private StreamObserver<NotificationResponse> notificationResponseObserver;

    @Mock
    private StreamObserver<NotificationListResponse> listResponseObserver;

    @InjectMocks
    private NotificationServiceGrpcImpl notificationService;

    private List<Notification> notificationList;

    @BeforeEach
    void setUp() {
        Notification notif1 = new Notification();
        notif1.setId("NOTIF-001");
        notif1.setAccountId("ACC-001");
        notif1.setMessage("Withdrawal of $500");
        notif1.setType("TRANSACTION");
        notif1.setChannel("APP_PUSH");
        notif1.setSentAt(LocalDateTime.now());

        Notification notif2 = new Notification();
        notif2.setId("NOTIF-002");
        notif2.setAccountId("ACC-001");
        notif2.setMessage("Low balance alert");
        notif2.setType("LOW_BALANCE");
        notif2.setChannel("SMS");
        notif2.setSentAt(LocalDateTime.now());

        notificationList = Arrays.asList(notif1, notif2);
    }

    // ========== sendTransactionAlert Tests ==========

    @Test
    void sendTransactionAlert_Success() {
        TransactionAlertRequest request = TransactionAlertRequest.newBuilder()
                .setAccountId("ACC-001")
                .setTransactionDetails("Withdrawal of $500 from ATM-001")
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId("NOTIF-NEW");
            return n;
        });

        notificationService.sendTransactionAlert(request, notificationResponseObserver);

        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notifCaptor.capture());
        Notification savedNotif = notifCaptor.getValue();

        assertEquals("ACC-001", savedNotif.getAccountId());
        assertEquals("Withdrawal of $500 from ATM-001", savedNotif.getMessage());
        assertEquals("TRANSACTION", savedNotif.getType());
        assertEquals("APP_PUSH", savedNotif.getChannel());
        assertNotNull(savedNotif.getSentAt());

        verify(notificationResponseObserver).onNext(argThat(response ->
                response.getStatus().equals("SENT")
        ));
        verify(notificationResponseObserver).onCompleted();
    }

    @Test
    void sendTransactionAlert_EmptyDetails() {
        TransactionAlertRequest request = TransactionAlertRequest.newBuilder()
                .setAccountId("ACC-001")
                .setTransactionDetails("")
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.sendTransactionAlert(request, notificationResponseObserver);

        verify(notificationRepository).save(argThat(notif ->
                notif.getMessage().equals("")
        ));
        verify(notificationResponseObserver).onCompleted();
    }

    @Test
    void sendTransactionAlert_VerifyTimestamp() {
        TransactionAlertRequest request = TransactionAlertRequest.newBuilder()
                .setAccountId("ACC-001")
                .setTransactionDetails("Deposit of $1000")
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.sendTransactionAlert(request, notificationResponseObserver);

        verify(notificationRepository).save(argThat(notif ->
                notif.getSentAt() != null &&
                notif.getSentAt().isBefore(LocalDateTime.now().plusSeconds(1))
        ));
        verify(notificationResponseObserver).onCompleted();
    }

    // ========== sendLowBalanceAlert Tests ==========

    @Test
    void sendLowBalanceAlert_NotImplemented() {
        NotificationAccountIdRequest request = NotificationAccountIdRequest.newBuilder()
                .setAccountId("ACC-001")
                .build();

        // Method is not implemented - just verify it doesn't throw
        assertDoesNotThrow(() ->
                notificationService.sendLowBalanceAlert(request, notificationResponseObserver)
        );
    }

    // ========== sendFraudAlert Tests ==========

    @Test
    void sendFraudAlert_NotImplemented() {
        FraudAlertRequest request = FraudAlertRequest.newBuilder()
                .setAccountId("ACC-001")
                .setFraudDetails("Suspicious activity detected")
                .build();

        // Method is not implemented - just verify it doesn't throw
        assertDoesNotThrow(() ->
                notificationService.sendFraudAlert(request, notificationResponseObserver)
        );
    }

    // ========== getNotifications Tests ==========

    @Test
    void getNotifications_Success() {
        when(notificationRepository.findByAccountId("ACC-001"))
                .thenReturn(notificationList);

        NotificationAccountIdRequest request = NotificationAccountIdRequest.newBuilder()
                .setAccountId("ACC-001")
                .build();

        notificationService.getNotifications(request, listResponseObserver);

        verify(notificationRepository).findByAccountId("ACC-001");
        verify(listResponseObserver).onNext(argThat(response ->
                response.getNotifications() != null &&
                response.getNotifications().contains("Withdrawal") &&
                response.getNotifications().contains("Low balance")
        ));
        verify(listResponseObserver).onCompleted();
    }

    @Test
    void getNotifications_NoNotifications() {
        when(notificationRepository.findByAccountId("ACC-999"))
                .thenReturn(List.of());

        NotificationAccountIdRequest request = NotificationAccountIdRequest.newBuilder()
                .setAccountId("ACC-999")
                .build();

        notificationService.getNotifications(request, listResponseObserver);

        verify(listResponseObserver).onNext(argThat(response ->
                response.getNotifications() != null
        ));
        verify(listResponseObserver).onCompleted();
    }

    @Test
    void getNotifications_VerifyFormattedOutput() {
        when(notificationRepository.findByAccountId("ACC-001"))
                .thenReturn(notificationList);

        NotificationAccountIdRequest request = NotificationAccountIdRequest.newBuilder()
                .setAccountId("ACC-001")
                .build();

        notificationService.getNotifications(request, listResponseObserver);

        verify(listResponseObserver).onNext(argThat(response -> {
            String notifications = response.getNotifications();
            return notifications.contains("Withdrawal of $500") &&
                    notifications.contains("Low balance alert") &&
                    notifications.contains(" | ");
        }));
        verify(listResponseObserver).onCompleted();
    }

    // ========== Multiple Notifications Test ==========

    @Test
    void sendMultipleTransactionAlerts() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Send first alert
        TransactionAlertRequest request1 = TransactionAlertRequest.newBuilder()
                .setAccountId("ACC-001")
                .setTransactionDetails("Withdrawal of $500")
                .build();
        notificationService.sendTransactionAlert(request1, notificationResponseObserver);

        // Send second alert
        TransactionAlertRequest request2 = TransactionAlertRequest.newBuilder()
                .setAccountId("ACC-001")
                .setTransactionDetails("Deposit of $1000")
                .build();
        notificationService.sendTransactionAlert(request2, notificationResponseObserver);

        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(notificationResponseObserver, times(2)).onCompleted();
    }
}
