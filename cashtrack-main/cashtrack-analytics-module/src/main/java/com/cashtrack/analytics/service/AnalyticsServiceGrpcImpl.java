package com.cashtrack.analytics.service;

import com.cashtrack.api.*;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.concurrent.TimeUnit;

@GrpcService
public class AnalyticsServiceGrpcImpl extends AnalyticsServiceGrpc.AnalyticsServiceImplBase {
    @GrpcClient("machineService")
    private MachineServiceGrpc.MachineServiceBlockingStub machineServiceBlockingStub;

    @GrpcClient("accountService")
    private AccountServiceGrpc.AccountServiceBlockingStub accountServiceBlockingStub;

    @Override
    public void getTransactionAnalytics(AnalyticsRequest request, StreamObserver<AnalyticsResponse> responseObserver) {
        responseObserver.onNext(AnalyticsResponse.newBuilder().setData("{\"range\":\"" + request.getDateRange() + "\"}").build());
        responseObserver.onCompleted();
    }

    @Override
    public void getATMUsageStats(AnalyticsATMIdRequest request, StreamObserver<AnalyticsResponse> responseObserver) {
        ATMCashResponse atmCashResponse;
        try {
            atmCashResponse = machineServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).getATMCashStatus(
                    ATMIdRequest.newBuilder().setAtmId(request.getAtmId()).build());
        } catch (Exception ex) {
            responseObserver.onNext(AnalyticsResponse.newBuilder()
                    .setData("{\"atmId\":\"" + request.getAtmId() + "\",\"error\":\"machine_service_unavailable\"}")
                    .build());
            responseObserver.onCompleted();
            return;
        }
        responseObserver.onNext(AnalyticsResponse.newBuilder()
                .setData("{\"atmId\":\"" + request.getAtmId() + "\",\"cashAvailable\":" + atmCashResponse.getCashAvailable() + "}")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getRevenueReport(RevenueRequest request, StreamObserver<AnalyticsResponse> responseObserver) {
        responseObserver.onNext(AnalyticsResponse.newBuilder()
                .setData("{\"report\":\"revenue\",\"range\":\"" + request.getDateRange() + "\"}")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getCustomerInsights(AnalyticsAccountIdRequest request, StreamObserver<AnalyticsResponse> responseObserver) {
        AccountSummaryResponse accountSummary;
        try {
            accountSummary = accountServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).getAccountSummary(
                    AccountIdRequest.newBuilder().setAccountId(request.getAccountId()).build());
        } catch (Exception ex) {
            responseObserver.onNext(AnalyticsResponse.newBuilder()
                    .setData("{\"accountId\":\"" + request.getAccountId() + "\",\"error\":\"account_service_unavailable\"}")
                    .build());
            responseObserver.onCompleted();
            return;
        }
        responseObserver.onNext(AnalyticsResponse.newBuilder()
                .setData("{\"accountId\":\"" + request.getAccountId() + "\",\"status\":\"" + accountSummary.getStatus() + "\",\"balance\":" + accountSummary.getBalance() + "}")
                .build());
        responseObserver.onCompleted();
    }
}