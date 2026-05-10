package com.cashtrack.notification.service;

import com.cashtrack.api.*;
import com.cashtrack.notification.entity.Notification;
import com.cashtrack.notification.repository.NotificationRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@GrpcService
public class NotificationServiceGrpcImpl extends NotificationServiceGrpc.NotificationServiceImplBase {

    @Autowired
    private NotificationRepository notificationRepository;

    @GrpcClient("accountService")
    private AccountServiceGrpc.AccountServiceBlockingStub accountServiceBlockingStub;

    @Override
    public void sendTransactionAlert(TransactionAlertRequest request, StreamObserver<NotificationResponse> responseObserver) {
        Notification notification = new Notification();
        notification.setAccountId(request.getAccountId());
        notification.setMessage(request.getTransactionDetails());
        notification.setType("TRANSACTION");
        notification.setChannel("APP_PUSH");
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);

        responseObserver.onNext(NotificationResponse.newBuilder()
                .setStatus("SENT")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void sendLowBalanceAlert(NotificationAccountIdRequest request, StreamObserver<NotificationResponse> responseObserver) {
        AccountSummaryResponse accountSummary = accountServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).getAccountSummary(
                AccountIdRequest.newBuilder().setAccountId(request.getAccountId()).build());
        if ("NOT_FOUND".equalsIgnoreCase(accountSummary.getStatus())) {
            responseObserver.onNext(NotificationResponse.newBuilder().setStatus("FAILED_ACCOUNT_NOT_FOUND").build());
            responseObserver.onCompleted();
            return;
        }

        Notification notification = new Notification();
        notification.setAccountId(request.getAccountId());
        notification.setMessage("Low balance detected: " + accountSummary.getBalance());
        notification.setType("LOW_BALANCE");
        notification.setChannel("APP_PUSH");
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);

        responseObserver.onNext(NotificationResponse.newBuilder().setStatus("SENT").build());
        responseObserver.onCompleted();
    }

    @Override
    public void sendFraudAlert(FraudAlertRequest request, StreamObserver<NotificationResponse> responseObserver) {
        Notification notification = new Notification();
        notification.setAccountId(request.getAccountId());
        notification.setMessage(request.getFraudDetails());
        notification.setType("FRAUD");
        notification.setChannel("APP_PUSH");
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);

        responseObserver.onNext(NotificationResponse.newBuilder().setStatus("SENT").build());
        responseObserver.onCompleted();
    }

    @Override
    public void getNotifications(NotificationAccountIdRequest request, StreamObserver<NotificationListResponse> responseObserver) {
        AccountSummaryResponse accountSummary = accountServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).getAccountSummary(
                AccountIdRequest.newBuilder().setAccountId(request.getAccountId()).build());
        if ("NOT_FOUND".equalsIgnoreCase(accountSummary.getStatus())) {
            responseObserver.onNext(NotificationListResponse.newBuilder().setNotifications("ACCOUNT_NOT_FOUND").build());
            responseObserver.onCompleted();
            return;
        }

        var notifications = notificationRepository.findByAccountId(request.getAccountId());
        StringBuilder sb = new StringBuilder();
        notifications.forEach(n -> sb.append(n.getMessage()).append(" | "));
        
        responseObserver.onNext(NotificationListResponse.newBuilder()
                .setNotifications(sb.toString())
                .build());
        responseObserver.onCompleted();
    }
}