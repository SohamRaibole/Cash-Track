package com.cashtrack.withdrawal.service;

import com.cashtrack.api.*;
import com.cashtrack.withdrawal.entity.WithdrawalTransaction;
import com.cashtrack.withdrawal.repository.WithdrawalRepository;
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
public class WithdrawalServiceTest {

    @Mock
    private WithdrawalRepository withdrawalRepository;

    @Mock
    private StreamObserver<TransactionResponse> responseObserver;

    @InjectMocks
    private WithdrawalServiceGrpcImpl withdrawalService;

    private WithdrawalTransaction activeTransaction;

    @BeforeEach
    void setUp() {
        activeTransaction = new WithdrawalTransaction();
        activeTransaction.setTransactionId("TXN-W-001");
        activeTransaction.setAccountId("ACC-001");
        activeTransaction.setAtmId("ATM-001");
        activeTransaction.setAmount(500.0);
        activeTransaction.setStatus("INITIATED");
    }

    // ========== initiateWithdrawal Tests ==========

    @Test
    void initiateWithdrawal_Success() {
        WithdrawalRequest request = WithdrawalRequest.newBuilder()
                .setAccountId("ACC-001")
                .setAtmId("ATM-001")
                .setAmount(500.0)
                .build();

        when(withdrawalRepository.save(any(WithdrawalTransaction.class))).thenAnswer(invocation -> {
            WithdrawalTransaction tx = invocation.getArgument(0);
            tx.setTransactionId("TXN-W-NEW");
            return tx;
        });

        withdrawalService.initiateWithdrawal(request, responseObserver);

        ArgumentCaptor<WithdrawalTransaction> txCaptor = ArgumentCaptor.forClass(WithdrawalTransaction.class);
        verify(withdrawalRepository).save(txCaptor.capture());
        WithdrawalTransaction savedTx = txCaptor.getValue();

        assertEquals("ACC-001", savedTx.getAccountId());
        assertEquals("ATM-001", savedTx.getAtmId());
        assertEquals(500.0, savedTx.getAmount());
        assertEquals("INITIATED", savedTx.getStatus());

        verify(responseObserver).onNext(argThat(response ->
                response.getTransactionId().equals("TXN-W-NEW") &&
                response.getStatus().equals("INITIATED") &&
                response.getMessage().equals("Withdrawal initiated")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void initiateWithdrawal_LargeAmount() {
        WithdrawalRequest request = WithdrawalRequest.newBuilder()
                .setAccountId("ACC-001")
                .setAtmId("ATM-001")
                .setAmount(10000.0)
                .build();

        when(withdrawalRepository.save(any(WithdrawalTransaction.class))).thenAnswer(invocation -> {
            WithdrawalTransaction tx = invocation.getArgument(0);
            // Simulate @PrePersist behavior
            if (tx.getTransactionId() == null) {
                tx.setTransactionId(java.util.UUID.randomUUID().toString());
            }
            return tx;
        });

        withdrawalService.initiateWithdrawal(request, responseObserver);

        verify(withdrawalRepository).save(argThat(tx -> tx.getAmount() == 10000.0 && tx.getTransactionId() != null));
        verify(responseObserver).onNext(argThat(response ->
                response.getTransactionId() != null &&
                response.getStatus().equals("INITIATED")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== validateWithdrawal Tests ==========

    @Test
    void validateWithdrawal_Success() {
        activeTransaction.setStatus("INITIATED");
        when(withdrawalRepository.findById("TXN-W-001")).thenReturn(Optional.of(activeTransaction));

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-W-001")
                .build();

        withdrawalService.validateWithdrawal(request, responseObserver);

        verify(withdrawalRepository).save(argThat(tx -> tx.getStatus().equals("AUTHORIZED")));
        verify(responseObserver).onNext(argThat(response ->
                response.getTransactionId().equals("TXN-W-001") &&
                response.getStatus().equals("AUTHORIZED") &&
                response.getMessage().contains("AUTHORIZED")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void validateWithdrawal_NotFound() {
        when(withdrawalRepository.findById("TXN-999")).thenReturn(Optional.empty());

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-999")
                .build();

        withdrawalService.validateWithdrawal(request, responseObserver);

        verify(withdrawalRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getTransactionId().equals("TXN-999") &&
                response.getStatus().equals("NOT_FOUND")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== dispenseCash Tests ==========

    @Test
    void dispenseCash_Success() {
        activeTransaction.setStatus("AUTHORIZED");
        when(withdrawalRepository.findById("TXN-W-001")).thenReturn(Optional.of(activeTransaction));

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-W-001")
                .build();

        withdrawalService.dispenseCash(request, responseObserver);

        verify(withdrawalRepository).save(argThat(tx -> tx.getStatus().equals("PROCESSING")));
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("PROCESSING")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void dispenseCash_NotFound() {
        when(withdrawalRepository.findById("TXN-999")).thenReturn(Optional.empty());

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-999")
                .build();

        withdrawalService.dispenseCash(request, responseObserver);

        verify(withdrawalRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("NOT_FOUND")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== confirmWithdrawal Tests ==========

    @Test
    void confirmWithdrawal_Success() {
        activeTransaction.setStatus("PROCESSING");
        when(withdrawalRepository.findById("TXN-W-001")).thenReturn(Optional.of(activeTransaction));

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-W-001")
                .build();

        withdrawalService.confirmWithdrawal(request, responseObserver);

        verify(withdrawalRepository).save(argThat(tx -> tx.getStatus().equals("COMPLETED")));
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("COMPLETED") &&
                response.getMessage().contains("COMPLETED")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void confirmWithdrawal_NotFound() {
        when(withdrawalRepository.findById("TXN-999")).thenReturn(Optional.empty());

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-999")
                .build();

        withdrawalService.confirmWithdrawal(request, responseObserver);

        verify(withdrawalRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("NOT_FOUND")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== reverseWithdrawal Tests ==========

    @Test
    void reverseWithdrawal_Success() {
        activeTransaction.setStatus("COMPLETED");
        when(withdrawalRepository.findById("TXN-W-001")).thenReturn(Optional.of(activeTransaction));

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-W-001")
                .build();

        withdrawalService.reverseWithdrawal(request, responseObserver);

        verify(withdrawalRepository).save(argThat(tx -> tx.getStatus().equals("REVERSED")));
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("REVERSED") &&
                response.getMessage().contains("REVERSED")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void reverseWithdrawal_NotFound() {
        when(withdrawalRepository.findById("TXN-999")).thenReturn(Optional.empty());

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-999")
                .build();

        withdrawalService.reverseWithdrawal(request, responseObserver);

        verify(withdrawalRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("NOT_FOUND")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== State Transition Tests ==========

    @Test
    void withdrawalFullLifecycle() {
        // Initiate
        WithdrawalRequest initiateReq = WithdrawalRequest.newBuilder()
                .setAccountId("ACC-001")
                .setAtmId("ATM-001")
                .setAmount(500.0)
                .build();

        when(withdrawalRepository.save(any(WithdrawalTransaction.class))).thenAnswer(invocation -> {
            WithdrawalTransaction tx = invocation.getArgument(0);
            tx.setTransactionId("TXN-W-LIFECYCLE");
            return tx;
        });

        withdrawalService.initiateWithdrawal(initiateReq, responseObserver);
        verify(responseObserver).onNext(argThat(r -> r.getStatus().equals("INITIATED")));

        // Validate
        activeTransaction.setTransactionId("TXN-W-LIFECYCLE");
        activeTransaction.setStatus("INITIATED");
        when(withdrawalRepository.findById("TXN-W-LIFECYCLE")).thenReturn(Optional.of(activeTransaction));

        TransactionIdRequest txIdReq = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-W-LIFECYCLE")
                .build();

        withdrawalService.validateWithdrawal(txIdReq, responseObserver);
        verify(responseObserver).onNext(argThat(r -> r.getStatus().equals("AUTHORIZED")));

        // Dispense
        activeTransaction.setStatus("AUTHORIZED");
        withdrawalService.dispenseCash(txIdReq, responseObserver);
        verify(responseObserver).onNext(argThat(r -> r.getStatus().equals("PROCESSING")));

        // Confirm
        activeTransaction.setStatus("PROCESSING");
        withdrawalService.confirmWithdrawal(txIdReq, responseObserver);
        verify(responseObserver).onNext(argThat(r -> r.getStatus().equals("COMPLETED")));

        verify(responseObserver, times(4)).onCompleted();
    }
}
