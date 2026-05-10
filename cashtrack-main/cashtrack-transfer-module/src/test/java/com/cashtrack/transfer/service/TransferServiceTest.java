package com.cashtrack.transfer.service;

import com.cashtrack.api.*;
import com.cashtrack.transfer.entity.TransferTransaction;
import com.cashtrack.transfer.repository.TransferRepository;
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
public class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private StreamObserver<TransactionResponse> responseObserver;

    @InjectMocks
    private TransferServiceGrpcImpl transferService;

    private TransferTransaction activeTransaction;

    @BeforeEach
    void setUp() {
        activeTransaction = new TransferTransaction();
        activeTransaction.setTransactionId("TXN-T-001");
        activeTransaction.setSourceAccountId("ACC-001");
        activeTransaction.setTargetAccountId("ACC-002");
        activeTransaction.setAmount(750.0);
        activeTransaction.setStatus("INITIATED");
    }

    // ========== initiateTransfer Tests ==========

    @Test
    void initiateTransfer_Success() {
        TransferRequest request = TransferRequest.newBuilder()
                .setSourceAccountId("ACC-001")
                .setTargetAccountId("ACC-002")
                .setAmount(750.0)
                .build();

        when(transferRepository.save(any(TransferTransaction.class))).thenAnswer(invocation -> {
            TransferTransaction tx = invocation.getArgument(0);
            tx.setTransactionId("TXN-T-NEW");
            return tx;
        });

        transferService.initiateTransfer(request, responseObserver);

        ArgumentCaptor<TransferTransaction> txCaptor = ArgumentCaptor.forClass(TransferTransaction.class);
        verify(transferRepository).save(txCaptor.capture());
        TransferTransaction savedTx = txCaptor.getValue();

        assertEquals("ACC-001", savedTx.getSourceAccountId());
        assertEquals("ACC-002", savedTx.getTargetAccountId());
        assertEquals(750.0, savedTx.getAmount());
        assertEquals("INITIATED", savedTx.getStatus());

        verify(responseObserver).onNext(argThat(response ->
                response.getTransactionId().equals("TXN-T-NEW") &&
                response.getStatus().equals("INITIATED") &&
                response.getMessage().equals("Transfer initiated")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void initiateTransfer_SameSourceAndTarget() {
        TransferRequest request = TransferRequest.newBuilder()
                .setSourceAccountId("ACC-001")
                .setTargetAccountId("ACC-001")
                .setAmount(100.0)
                .build();

        when(transferRepository.save(any(TransferTransaction.class))).thenAnswer(invocation -> {
            TransferTransaction tx = invocation.getArgument(0);
            // Simulate @PrePersist behavior
            if (tx.getTransactionId() == null) {
                tx.setTransactionId(java.util.UUID.randomUUID().toString());
            }
            return tx;
        });

        transferService.initiateTransfer(request, responseObserver);

        verify(transferRepository).save(argThat(tx ->
                tx.getSourceAccountId().equals("ACC-001") &&
                tx.getTargetAccountId().equals("ACC-001") &&
                tx.getTransactionId() != null
        ));
        verify(responseObserver).onNext(argThat(response ->
                response.getTransactionId() != null &&
                response.getStatus().equals("INITIATED")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== validateTransfer Tests ==========

    @Test
    void validateTransfer_Success() {
        activeTransaction.setStatus("INITIATED");
        when(transferRepository.findById("TXN-T-001")).thenReturn(Optional.of(activeTransaction));

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-T-001")
                .build();

        transferService.validateTransfer(request, responseObserver);

        verify(transferRepository).save(argThat(tx -> tx.getStatus().equals("VALIDATED")));
        verify(responseObserver).onNext(argThat(response ->
                response.getTransactionId().equals("TXN-T-001") &&
                response.getStatus().equals("VALIDATED") &&
                response.getMessage().contains("VALIDATED")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void validateTransfer_NotFound() {
        when(transferRepository.findById("TXN-999")).thenReturn(Optional.empty());

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-999")
                .build();

        transferService.validateTransfer(request, responseObserver);

        verify(transferRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getTransactionId().equals("TXN-999") &&
                response.getStatus().equals("NOT_FOUND")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== executeTransfer Tests ==========

    @Test
    void executeTransfer_Success() {
        activeTransaction.setStatus("VALIDATED");
        when(transferRepository.findById("TXN-T-001")).thenReturn(Optional.of(activeTransaction));

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-T-001")
                .build();

        transferService.executeTransfer(request, responseObserver);

        verify(transferRepository).save(argThat(tx -> tx.getStatus().equals("EXECUTED")));
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("EXECUTED")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void executeTransfer_NotFound() {
        when(transferRepository.findById("TXN-999")).thenReturn(Optional.empty());

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-999")
                .build();

        transferService.executeTransfer(request, responseObserver);

        verify(transferRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("NOT_FOUND")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== confirmTransfer Tests ==========

    @Test
    void confirmTransfer_Success() {
        activeTransaction.setStatus("EXECUTED");
        when(transferRepository.findById("TXN-T-001")).thenReturn(Optional.of(activeTransaction));

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-T-001")
                .build();

        transferService.confirmTransfer(request, responseObserver);

        verify(transferRepository).save(argThat(tx -> tx.getStatus().equals("COMPLETED")));
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("COMPLETED") &&
                response.getMessage().contains("COMPLETED")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void confirmTransfer_NotFound() {
        when(transferRepository.findById("TXN-999")).thenReturn(Optional.empty());

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-999")
                .build();

        transferService.confirmTransfer(request, responseObserver);

        verify(transferRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("NOT_FOUND")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== rollbackTransfer Tests ==========

    @Test
    void rollbackTransfer_Success() {
        activeTransaction.setStatus("EXECUTED");
        when(transferRepository.findById("TXN-T-001")).thenReturn(Optional.of(activeTransaction));

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-T-001")
                .build();

        transferService.rollbackTransfer(request, responseObserver);

        verify(transferRepository).save(argThat(tx -> tx.getStatus().equals("ROLLBACKED")));
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("ROLLBACKED") &&
                response.getMessage().contains("ROLLBACKED")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void rollbackTransfer_NotFound() {
        when(transferRepository.findById("TXN-999")).thenReturn(Optional.empty());

        TransactionIdRequest request = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-999")
                .build();

        transferService.rollbackTransfer(request, responseObserver);

        verify(transferRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("NOT_FOUND")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== Full Lifecycle Test ==========

    @Test
    void transferFullLifecycle() {
        // Initiate
        TransferRequest request = TransferRequest.newBuilder()
                .setSourceAccountId("ACC-001")
                .setTargetAccountId("ACC-002")
                .setAmount(750.0)
                .build();

        when(transferRepository.save(any(TransferTransaction.class))).thenAnswer(invocation -> {
            TransferTransaction tx = invocation.getArgument(0);
            tx.setTransactionId("TXN-T-LIFECYCLE");
            return tx;
        });

        transferService.initiateTransfer(request, responseObserver);
        verify(responseObserver).onNext(argThat(r -> r.getStatus().equals("INITIATED")));

        // Validate
        activeTransaction.setTransactionId("TXN-T-LIFECYCLE");
        activeTransaction.setStatus("INITIATED");
        when(transferRepository.findById("TXN-T-LIFECYCLE")).thenReturn(Optional.of(activeTransaction));

        TransactionIdRequest txIdReq = TransactionIdRequest.newBuilder()
                .setTransactionId("TXN-T-LIFECYCLE")
                .build();

        transferService.validateTransfer(txIdReq, responseObserver);
        verify(responseObserver).onNext(argThat(r -> r.getStatus().equals("VALIDATED")));

        // Execute
        activeTransaction.setStatus("VALIDATED");
        transferService.executeTransfer(txIdReq, responseObserver);
        verify(responseObserver).onNext(argThat(r -> r.getStatus().equals("EXECUTED")));

        // Confirm
        activeTransaction.setStatus("EXECUTED");
        transferService.confirmTransfer(txIdReq, responseObserver);
        verify(responseObserver).onNext(argThat(r -> r.getStatus().equals("COMPLETED")));

        verify(responseObserver, times(4)).onCompleted();
    }
}
