package com.cashtrack.withdrawal.service;

import com.cashtrack.api.*;
import com.cashtrack.withdrawal.entity.WithdrawalTransaction;
import com.cashtrack.withdrawal.repository.WithdrawalRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@GrpcService
public class WithdrawalServiceGrpcImpl extends WithdrawalServiceGrpc.WithdrawalServiceImplBase {

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @GrpcClient("accountService")
    private AccountServiceGrpc.AccountServiceBlockingStub accountServiceBlockingStub;

    @Override
    @Transactional
    public void initiateWithdrawal(WithdrawalRequest request, StreamObserver<TransactionResponse> responseObserver) {
        if (request.getAmount() <= 0) {
            responseObserver.onNext(TransactionResponse.newBuilder()
                    .setTransactionId("")
                    .setStatus("FAILED")
                    .setMessage("Withdrawal amount must be greater than zero")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        AccountSummaryResponse accountSummary;
        try {
            accountSummary = accountServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).getAccountSummary(
                    AccountIdRequest.newBuilder().setAccountId(request.getAccountId()).build());
        } catch (Exception ex) {
            responseObserver.onNext(TransactionResponse.newBuilder()
                    .setTransactionId("")
                    .setStatus("FAILED")
                    .setMessage("Account service unavailable")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if ("NOT_FOUND".equalsIgnoreCase(accountSummary.getStatus())) {
            responseObserver.onNext(TransactionResponse.newBuilder()
                    .setTransactionId("")
                    .setStatus("FAILED")
                    .setMessage("Account not found")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (!"ACTIVE".equalsIgnoreCase(accountSummary.getStatus())) {
            responseObserver.onNext(TransactionResponse.newBuilder()
                    .setTransactionId("")
                    .setStatus("FAILED")
                    .setMessage("Account is not active")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (accountSummary.getBalance() < request.getAmount()) {
            responseObserver.onNext(TransactionResponse.newBuilder()
                    .setTransactionId("")
                    .setStatus("FAILED")
                    .setMessage("Insufficient account balance")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        WithdrawalTransaction tx = new WithdrawalTransaction();
        tx.setAccountId(request.getAccountId());
        tx.setAtmId(request.getAtmId());
        tx.setAmount(request.getAmount());
        tx.setStatus("INITIATED");

        withdrawalRepository.save(tx);

        responseObserver.onNext(TransactionResponse.newBuilder()
                .setTransactionId(tx.getTransactionId())
                .setStatus(tx.getStatus())
                .setMessage("Withdrawal initiated")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void validateWithdrawal(TransactionIdRequest request, StreamObserver<TransactionResponse> responseObserver) {
        updateStatus(request.getTransactionId(), "AUTHORIZED", responseObserver);
    }

    @Override
    @Transactional
    public void dispenseCash(TransactionIdRequest request, StreamObserver<TransactionResponse> responseObserver) {
        updateStatus(request.getTransactionId(), "PROCESSING", responseObserver);
    }

    @Override
    @Transactional
    public void confirmWithdrawal(TransactionIdRequest request, StreamObserver<TransactionResponse> responseObserver) {
        updateStatus(request.getTransactionId(), "COMPLETED", responseObserver);
    }

    @Override
    @Transactional
    public void reverseWithdrawal(TransactionIdRequest request, StreamObserver<TransactionResponse> responseObserver) {
        updateStatus(request.getTransactionId(), "REVERSED", responseObserver);
    }

    private void updateStatus(String txId, String status, StreamObserver<TransactionResponse> observer) {
        Optional<WithdrawalTransaction> txOpt = withdrawalRepository.findById(txId);
        
        if (txOpt.isPresent()) {
            WithdrawalTransaction tx = txOpt.get();
            tx.setStatus(status);
            withdrawalRepository.save(tx);
            
            observer.onNext(TransactionResponse.newBuilder()
                    .setTransactionId(tx.getTransactionId())
                    .setStatus(tx.getStatus())
                    .setMessage("Status updated to " + status)
                    .build());
        } else {
            observer.onNext(TransactionResponse.newBuilder()
                    .setTransactionId(txId)
                    .setStatus("NOT_FOUND")
                    .setMessage("Transaction not found")
                    .build());
        }
        observer.onCompleted();
    }
}
