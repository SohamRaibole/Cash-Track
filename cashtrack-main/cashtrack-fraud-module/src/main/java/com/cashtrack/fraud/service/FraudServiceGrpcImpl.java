package com.cashtrack.fraud.service;

import com.cashtrack.api.*;
import com.cashtrack.fraud.entity.FraudAlert;
import com.cashtrack.fraud.repository.FraudRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@GrpcService
public class FraudServiceGrpcImpl extends FraudServiceGrpc.FraudServiceImplBase {

    @Autowired
    private FraudRepository fraudRepository;

    @GrpcClient("notificationService")
    private NotificationServiceGrpc.NotificationServiceBlockingStub notificationServiceBlockingStub;

    @Override
    public void detectFraudulentTransaction(FraudTransactionIdRequest request, StreamObserver<FraudResponse> responseObserver) {
        // Mock logic: Transactions over 10000 are flagged
        String status = "SAFE";
        // In a real scenario, we'd fetch the transaction and check its amount
        
        responseObserver.onNext(FraudResponse.newBuilder()
                .setStatus(status)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void flagSuspiciousActivity(SuspiciousActivityRequest request, StreamObserver<FraudResponse> responseObserver) {
        FraudAlert alert = new FraudAlert();
        alert.setAccountId(request.getAccountId());
        alert.setAlertType("MANUAL_FLAG");
        alert.setSeverity("HIGH");
        alert.setTimestamp(LocalDateTime.now());
        fraudRepository.save(alert);

        try {
            notificationServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).sendFraudAlert(FraudAlertRequest.newBuilder()
                    .setAccountId(request.getAccountId())
                    .setFraudDetails(request.getActivityDetails())
                    .build());
        } catch (Exception ignored) {
            // Notification errors should not fail fraud flagging.
        }

        responseObserver.onNext(FraudResponse.newBuilder()
                .setStatus("FLAGGED")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void analyzeTransactionPatterns(FraudAccountIdRequest request, StreamObserver<FraudResponse> responseObserver) {
        long suspiciousCount = fraudRepository.findByAccountId(request.getAccountId()).stream()
                .filter(a -> "HIGH".equalsIgnoreCase(a.getSeverity()))
                .count();

        responseObserver.onNext(FraudResponse.newBuilder()
                .setStatus(suspiciousCount >= 3 ? "HIGH_RISK" : "NORMAL")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getFraudReports(FraudReportRequest request, StreamObserver<FraudReportResponse> responseObserver) {
        responseObserver.onNext(FraudReportResponse.newBuilder()
                .setReportDetails("Fraud report generated for date " + request.getDate())
                .build());
        responseObserver.onCompleted();
    }
}