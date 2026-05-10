package com.cashtrack.deposit.service;

import com.cashtrack.api.*;
import com.cashtrack.deposit.entity.DepositTransaction;
import com.cashtrack.deposit.repository.DepositRepository;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DepositServiceTest {

    @Mock
    private DepositRepository depositRepository;

    @Mock
    private StreamObserver<TransactionResponse> responseObserver;

    @InjectMocks
    private DepositServiceGrpcImpl depositService;

    private DepositTransaction activeTransaction;

    @BeforeEach
    void setUp() {
        activeTransaction = new DepositTransaction();
        activeTransaction.setTransactionId("TXN-D-001");
        activeTransaction.setAccountId("ACC-001");
        activeTransaction.setAtmId("ATM-001");
        activeTransaction.setAmount(1000.0);
        activeTransaction.setStatus("Initiated");
    }

    // ========== initiateDeposit Tests ==========

    @Test
    void initiateDeposit_Success() {
        DepositRequest request = DepositRequest.newBuilder()
                .setAccountId("ACC-001")
                .setAtmId("ATM-001")
                .setAmount(1000.0)
                .build();

        when(depositRepository.save(any(DepositTransaction.class))).thenAnswer(invocation -> {
            DepositTransaction tx = invocation.getArgument(0);
            tx.setTransactionId("TXN-D-NEW");
            return tx;
        });

        depositService.initiateDeposit(request, responseObserver);

        ArgumentCaptor<DepositTransaction> txCaptor = ArgumentCaptor.forClass(DepositTransaction.class);
        verify(depositRepository).save(txCaptor.capture());
        DepositTransaction savedTx = txCaptor.getValue();

        assertEquals("ACC-001", savedTx.getAccountId());
        assertEquals("ATM-001", savedTx.getAtmId());
        assertEquals(1000.0, savedTx.getAmount());
        assertEquals("Initiated", savedTx.getStatus());

        verify(responseObserver).onNext(argThat(response ->
                response.getTransactionId().equals("TXN-D-NEW") &&
                response.getStatus().equals("Initiated") &&
                response.getMessage().equals("Deposit initiated successfully")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void initiateDeposit_ZeroAmount() {
        DepositRequest request = DepositRequest.newBuilder()
                .setAccountId("ACC-001")
                .setAtmId("ATM-001")
                .setAmount(0.0)
                .build();

        when(depositRepository.save(any(DepositTransaction.class))).thenAnswer(invocation -> {
            DepositTransaction tx = invocation.getArgument(0);
            tx.setTransactionId("TXN-D-ZERO");
            return tx;
        });

        depositService.initiateDeposit(request, responseObserver);

        verify(depositRepository).save(argThat(tx -> tx != null && tx.getAmount() == 0.0));
        verify(responseObserver).onCompleted();
    }

    // ========== acceptCash Tests ==========

    @Test
    void acceptCash_Success() {
        activeTransaction.setStatus("Initiated");
        when(depositRepository.findById("TXN-D-001")).thenReturn(Optional.of(activeTransaction));

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-D-001")
                .build();

        depositService.acceptCash(request, responseObserver);

        verify(depositRepository).save(argThat(tx -> tx.getStatus().equals("Processing")));
        verify(responseObserver).onNext(argThat(response ->
                response.getTransactionId().equals("TXN-D-001") &&
                response.getStatus().equals("Processing") &&
                response.getMessage().contains("counted and processed")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void acceptCash_NotFound() {
        when(depositRepository.findById("TXN-999")).thenReturn(Optional.empty());

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-999")
                .build();

        depositService.acceptCash(request, responseObserver);

        verify(depositRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getTransactionId().equals("TXN-999") &&
                response.getStatus().equals("NOT_FOUND")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== validateDeposit Tests ==========

    @Test
    void validateDeposit_Success() {
        activeTransaction.setStatus("Processing");
        when(depositRepository.findById("TXN-D-001")).thenReturn(Optional.of(activeTransaction));

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-D-001")
                .build();

        depositService.validateDeposit(request, responseObserver);

        verify(depositRepository).save(argThat(tx -> tx.getStatus().equals("Authorized")));
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("Authorized") &&
                response.getMessage().contains("authorized by the system")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void validateDeposit_NotFound() {
        when(depositRepository.findById("TXN-999")).thenReturn(Optional.empty());

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-999")
                .build();

        depositService.validateDeposit(request, responseObserver);

        verify(depositRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("NOT_FOUND")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== confirmDeposit Tests ==========

    @Test
    void confirmDeposit_Success() {
        activeTransaction.setStatus("Authorized");
        when(depositRepository.findById("TXN-D-001")).thenReturn(Optional.of(activeTransaction));

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-D-001")
                .build();

        depositService.confirmDeposit(request, responseObserver);

        verify(depositRepository).save(argThat(tx -> tx.getStatus().equals("Completed")));
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("Completed") &&
                response.getMessage().contains("posted to account")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void confirmDeposit_NotFound() {
        when(depositRepository.findById("TXN-999")).thenReturn(Optional.empty());

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-999")
                .build();

        depositService.confirmDeposit(request, responseObserver);

        verify(depositRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("NOT_FOUND")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== reconcileDeposit Tests ==========

    @Test
    void reconcileDeposit_Success() {
        activeTransaction.setStatus("Completed");
        when(depositRepository.findById("TXN-D-001")).thenReturn(Optional.of(activeTransaction));

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-D-001")
                .build();

        depositService.reconcileDeposit(request, responseObserver);

        verify(depositRepository).save(argThat(tx -> tx.getStatus().equals("Reversed")));
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("Reversed") &&
                response.getMessage().contains("returned")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void reconcileDeposit_NotFound() {
        when(depositRepository.findById("TXN-999")).thenReturn(Optional.empty());

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-999")
                .build();

        depositService.reconcileDeposit(request, responseObserver);

        verify(depositRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("NOT_FOUND")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== State Transition Tests ==========

    @Test
    void depositFullLifecycle() {
        // Initiate
        DepositRequest initiateReq = DepositRequest.newBuilder()
                .setAccountId("ACC-001")
                .setAtmId("ATM-001")
                .setAmount(1000.0)
                .build();

        when(depositRepository.save(any(DepositTransaction.class))).thenAnswer(invocation -> {
            DepositTransaction tx = invocation.getArgument(0);
            tx.setTransactionId("TXN-D-LIFECYCLE");
            return tx;
        });

        depositService.initiateDeposit(initiateReq, responseObserver);
        verify(responseObserver).onNext(argThat(r -> r.getStatus().equals("Initiated")));

        // Accept Cash
        activeTransaction.setTransactionId("TXN-D-LIFECYCLE");
        activeTransaction.setStatus("Initiated");
        when(depositRepository.findById("TXN-D-LIFECYCLE")).thenReturn(Optional.of(activeTransaction));

        TransactionIdRequest txIdReq = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-D-LIFECYCLE")
                .build();

        depositService.acceptCash(txIdReq, responseObserver);
        verify(responseObserver).onNext(argThat(r -> r.getStatus().equals("Processing")));

        // Validate
        activeTransaction.setStatus("Processing");
        depositService.validateDeposit(txIdReq, responseObserver);
        verify(responseObserver).onNext(argThat(r -> r.getStatus().equals("Authorized")));

        // Confirm
        activeTransaction.setStatus("Authorized");
        depositService.confirmDeposit(txIdReq, responseObserver);
        verify(responseObserver).onNext(argThat(r -> r.getStatus().equals("Completed")));

        verify(responseObserver, times(4)).onCompleted();
    }

}
