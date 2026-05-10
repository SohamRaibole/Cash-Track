package com.cashtrack.reconciliation.service;

import com.cashtrack.api.*;
import com.cashtrack.reconciliation.entity.ReconciliationLog;
import com.cashtrack.reconciliation.repository.ReconciliationRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

@GrpcService
public class ReconciliationServiceGrpcImpl extends ReconciliationServiceGrpc.ReconciliationServiceImplBase {

    @Autowired
    private ReconciliationRepository reconciliationRepository;

    @GrpcClient("analyticsService")
    private AnalyticsServiceGrpc.AnalyticsServiceBlockingStub analyticsServiceBlockingStub;

    @Override
    public void reconcileTransactions(ReconcileRequest request, StreamObserver<ReconcileResponse> responseObserver) {
        ReconciliationLog log = new ReconciliationLog();
        log.setReconciliationDate(LocalDate.parse(request.getDate()));
        log.setTotalTransactions(100); // Mock data
        log.setMatchedCount(98);
        log.setMismatchedCount(2);
        log.setStatus("DISCREPANCY_FOUND");
        reconciliationRepository.save(log);

        responseObserver.onNext(ReconcileResponse.newBuilder()
                .setStatus(log.getStatus())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void detectMismatch(MismatchRequest request, StreamObserver<MismatchResponse> responseObserver) {
        AnalyticsResponse analytics;
        try {
            analytics = analyticsServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).getTransactionAnalytics(
                    AnalyticsRequest.newBuilder().setDateRange(request.getDate()).build());
        } catch (Exception ex) {
            responseObserver.onNext(MismatchResponse.newBuilder()
                    .setStatus("FAILED")
                    .setMismatchDetails("Analytics service unavailable")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        responseObserver.onNext(MismatchResponse.newBuilder()
                .setStatus("SCANNED")
                .setMismatchDetails("Mismatch scan for " + request.getDate() + " using " + analytics.getData())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void resolveMismatch(ResolveMismatchRequest request, StreamObserver<ReconcileResponse> responseObserver) {
        responseObserver.onNext(ReconcileResponse.newBuilder()
                .setStatus("RESOLVED:" + request.getMismatchId())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void generateSettlementReport(SettlementRequest request, StreamObserver<SettlementResponse> responseObserver) {
        AnalyticsResponse analytics;
        try {
            analytics = analyticsServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).getRevenueReport(
                    RevenueRequest.newBuilder().setDateRange(request.getDate()).build());
        } catch (Exception ex) {
            responseObserver.onNext(SettlementResponse.newBuilder()
                    .setReportDetails("Analytics service unavailable for settlement date " + request.getDate())
                    .build());
            responseObserver.onCompleted();
            return;
        }
        responseObserver.onNext(SettlementResponse.newBuilder()
                .setReportDetails("Settlement report for " + request.getDate() + " based on " + analytics.getData())
                .build());
        responseObserver.onCompleted();
    }
}