package com.cashtrack.transfer.service;

import com.cashtrack.api.*;
import com.cashtrack.transfer.entity.TransferTransaction;
import com.cashtrack.transfer.repository.TransferRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@GrpcService
public class TransferServiceGrpcImpl extends TransferServiceGrpc.TransferServiceImplBase {

    @Autowired
    private TransferRepository transferRepository;

    @GrpcClient("accountService")
    private AccountServiceGrpc.AccountServiceBlockingStub accountServiceBlockingStub;

    @Override
    @Transactional
    public void initiateTransfer(TransferRequest request, StreamObserver<TransactionResponse> responseObserver) {
        if (request.getAmount() <= 0) {
            responseObserver.onNext(TransactionResponse.newBuilder()
                    .setTransactionId("")
                    .setStatus("FAILED")
                    .setMessage("Transfer amount must be greater than zero")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (request.getSourceAccountId().equals(request.getTargetAccountId())) {
            responseObserver.onNext(TransactionResponse.newBuilder()
                    .setTransactionId("")
                    .setStatus("FAILED")
                    .setMessage("Source and target account cannot be the same")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        AccountSummaryResponse sourceAccount;
        AccountSummaryResponse targetAccount;
        try {
            sourceAccount = accountServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).getAccountSummary(
                    AccountIdRequest.newBuilder().setAccountId(request.getSourceAccountId()).build());
            targetAccount = accountServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).getAccountSummary(
                    AccountIdRequest.newBuilder().setAccountId(request.getTargetAccountId()).build());
        } catch (Exception ex) {
            responseObserver.onNext(TransactionResponse.newBuilder()
                    .setTransactionId("")
                    .setStatus("FAILED")
                    .setMessage("Account service unavailable")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if ("NOT_FOUND".equalsIgnoreCase(sourceAccount.getStatus())
                || "NOT_FOUND".equalsIgnoreCase(targetAccount.getStatus())) {
            responseObserver.onNext(TransactionResponse.newBuilder()
                    .setTransactionId("")
                    .setStatus("FAILED")
                    .setMessage("Source or target account not found")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (!"ACTIVE".equalsIgnoreCase(sourceAccount.getStatus())
                || !"ACTIVE".equalsIgnoreCase(targetAccount.getStatus())) {
            responseObserver.onNext(TransactionResponse.newBuilder()
                    .setTransactionId("")
                    .setStatus("FAILED")
                    .setMessage("Both accounts must be active")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (sourceAccount.getBalance() < request.getAmount()) {
            responseObserver.onNext(TransactionResponse.newBuilder()
                    .setTransactionId("")
                    .setStatus("FAILED")
                    .setMessage("Insufficient balance in source account")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        TransferTransaction tx = new TransferTransaction();
        tx.setSourceAccountId(request.getSourceAccountId());
        tx.setTargetAccountId(request.getTargetAccountId());
        tx.setAmount(request.getAmount());
        tx.setStatus("INITIATED");

        transferRepository.save(tx);

        responseObserver.onNext(TransactionResponse.newBuilder()
                .setTransactionId(tx.getTransactionId())
                .setStatus(tx.getStatus())
                .setMessage("Transfer initiated")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void validateTransfer(TransactionIdRequest request, StreamObserver<TransactionResponse> responseObserver) {
        updateStatus(request.getTransactionId(), "VALIDATED", responseObserver);
    }

    @Override
    @Transactional
    public void executeTransfer(TransactionIdRequest request, StreamObserver<TransactionResponse> responseObserver) {
        updateStatus(request.getTransactionId(), "EXECUTED", responseObserver);
    }

    @Override
    @Transactional
    public void confirmTransfer(TransactionIdRequest request, StreamObserver<TransactionResponse> responseObserver) {
        updateStatus(request.getTransactionId(), "COMPLETED", responseObserver);
    }

    @Override
    @Transactional
    public void rollbackTransfer(TransactionIdRequest request, StreamObserver<TransactionResponse> responseObserver) {
        updateStatus(request.getTransactionId(), "ROLLBACKED", responseObserver);
    }

    private void updateStatus(String txId, String status, StreamObserver<TransactionResponse> observer) {
        Optional<TransferTransaction> txOpt = transferRepository.findById(txId);
        
        if (txOpt.isPresent()) {
            TransferTransaction tx = txOpt.get();
            tx.setStatus(status);
            transferRepository.save(tx);
            
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
