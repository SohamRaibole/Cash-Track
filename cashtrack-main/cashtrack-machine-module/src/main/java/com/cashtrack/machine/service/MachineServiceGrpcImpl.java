package com.cashtrack.machine.service;

import com.cashtrack.api.*;
import com.cashtrack.machine.entity.ATMMachine;
import com.cashtrack.machine.repository.ATMRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@GrpcService
public class MachineServiceGrpcImpl extends MachineServiceGrpc.MachineServiceImplBase {

    @Autowired
    private ATMRepository atmRepository;

    @GrpcClient("notificationService")
    private NotificationServiceGrpc.NotificationServiceBlockingStub notificationServiceBlockingStub;

    @Override
    public void registerATM(RegisterATMRequest request, StreamObserver<ATMResponse> responseObserver) {
        ATMMachine atm = new ATMMachine();
        atm.setLocation(request.getLocation());
        atm.setCashBalance(0.0);
        atm.setStatus("ACTIVE");
        atmRepository.save(atm);

        responseObserver.onNext(ATMResponse.newBuilder()
                .setAtmId(atm.getId())
                .setStatus(atm.getStatus())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getATMCashStatus(ATMIdRequest request, StreamObserver<ATMCashResponse> responseObserver) {
        Optional<ATMMachine> atmOpt = atmRepository.findById(request.getAtmId());
        if (atmOpt.isPresent()) {
            responseObserver.onNext(ATMCashResponse.newBuilder()
                    .setAtmId(atmOpt.get().getId())
                    .setCashAvailable(atmOpt.get().getCashBalance())
                    .build());
        } else {
            responseObserver.onNext(ATMCashResponse.newBuilder()
                    .setAtmId(request.getAtmId())
                    .setCashAvailable(-1)
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void updateATMStatus(ATMStatusRequest request, StreamObserver<ATMResponse> responseObserver) {
        Optional<ATMMachine> atmOpt = atmRepository.findById(request.getAtmId());
        if (atmOpt.isEmpty()) {
            responseObserver.onNext(ATMResponse.newBuilder().setAtmId(request.getAtmId()).setStatus("NOT_FOUND").build());
            responseObserver.onCompleted();
            return;
        }

        ATMMachine atm = atmOpt.get();
        atm.setStatus(request.getStatus());
        atmRepository.save(atm);
        responseObserver.onNext(ATMResponse.newBuilder().setAtmId(atm.getId()).setStatus(atm.getStatus()).build());
        responseObserver.onCompleted();
    }

    @Override
    public void loadCash(LoadCashRequest request, StreamObserver<ATMResponse> responseObserver) {
        Optional<ATMMachine> atmOpt = atmRepository.findById(request.getAtmId());
        if (atmOpt.isEmpty()) {
            responseObserver.onNext(ATMResponse.newBuilder().setAtmId(request.getAtmId()).setStatus("NOT_FOUND").build());
            responseObserver.onCompleted();
            return;
        }
        if (request.getAmount() <= 0) {
            responseObserver.onNext(ATMResponse.newBuilder().setAtmId(request.getAtmId()).setStatus("INVALID_AMOUNT").build());
            responseObserver.onCompleted();
            return;
        }

        ATMMachine atm = atmOpt.get();
        atm.setCashBalance(atm.getCashBalance() + request.getAmount());
        atmRepository.save(atm);
        responseObserver.onNext(ATMResponse.newBuilder().setAtmId(atm.getId()).setStatus("CASH_LOADED").build());
        responseObserver.onCompleted();
    }

    @Override
    public void reportATMFailure(ATMFailureRequest request, StreamObserver<ATMResponse> responseObserver) {
        Optional<ATMMachine> atmOpt = atmRepository.findById(request.getAtmId());
        if (atmOpt.isPresent()) {
            ATMMachine atm = atmOpt.get();
            atm.setStatus("FAILED");
            atmRepository.save(atm);
        }

        try {
            notificationServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).sendTransactionAlert(TransactionAlertRequest.newBuilder()
                    .setAccountId("ATM_ADMIN")
                    .setTransactionDetails("ATM failure for " + request.getAtmId() + ": " + request.getErrorDetails())
                    .build());
        } catch (Exception ignored) {
            // Notification errors should not fail ATM failure reporting.
        }

        responseObserver.onNext(ATMResponse.newBuilder()
                .setAtmId(request.getAtmId())
                .setStatus("FAILURE_REPORTED")
                .build());
        responseObserver.onCompleted();
    }
}