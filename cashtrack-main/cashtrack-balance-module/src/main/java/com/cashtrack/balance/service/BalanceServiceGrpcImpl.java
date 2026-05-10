package com.cashtrack.balance.service;

import com.cashtrack.api.*;
import com.cashtrack.balance.repository.TransactionHistoryRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

@GrpcService
public class BalanceServiceGrpcImpl extends BalanceServiceGrpc.BalanceServiceImplBase {

    @Autowired
    private TransactionHistoryRepository historyRepository;

    @GrpcClient("accountService")
    private AccountServiceGrpc.AccountServiceBlockingStub accountServiceBlockingStub;

    @Override
    public void getBalance(BalanceRequest request, StreamObserver<BalanceResponse> responseObserver) {
        AccountSummaryResponse accountSummary;
        try {
            accountSummary = accountServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).getAccountSummary(
                    AccountIdRequest.newBuilder().setAccountId(request.getAccountId()).build());
        } catch (Exception ex) {
            responseObserver.onNext(BalanceResponse.newBuilder()
                    .setAccountId(request.getAccountId())
                    .setBalance(0.0)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if ("NOT_FOUND".equalsIgnoreCase(accountSummary.getStatus())) {
            responseObserver.onNext(BalanceResponse.newBuilder()
                    .setAccountId(request.getAccountId())
                    .setBalance(0.0)
                    .build());
            responseObserver.onCompleted();
            return;
        }

        responseObserver.onNext(BalanceResponse.newBuilder()
                .setAccountId(request.getAccountId())
                .setBalance(accountSummary.getBalance())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getMiniStatement(StatementRequest request, StreamObserver<StatementResponse> responseObserver) {
        var history = historyRepository.findByAccountIdOrderByTimestampDesc(request.getAccountId());
        StringBuilder sb = new StringBuilder("Mini Statement: ");
        history.stream().limit(5).forEach(r -> sb.append(r.getType()).append(": ").append(r.getAmount()).append(" | "));
        
        responseObserver.onNext(StatementResponse.newBuilder()
                .setAccountId(request.getAccountId())
                .setStatementDetails(sb.toString())
                .build());
        responseObserver.onCompleted();
    }
}