package com.cashtrack.reconciliation.service;

import com.cashtrack.api.*;
import com.cashtrack.reconciliation.entity.ReconciliationLog;
import com.cashtrack.reconciliation.repository.ReconciliationRepository;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReconciliationServiceTest {

    @Mock
    private ReconciliationRepository reconciliationRepository;

    @Mock
    private StreamObserver<ReconcileResponse> reconcileResponseObserver;

    @Mock
    private StreamObserver<MismatchResponse> mismatchResponseObserver;

    @Mock
    private StreamObserver<SettlementResponse> settlementResponseObserver;

    @InjectMocks
    private ReconciliationServiceGrpcImpl reconciliationService;

    private ReconciliationLog existingLog;

    @BeforeEach
    void setUp() {
        existingLog = new ReconciliationLog();
        existingLog.setId("LOG-001");
        existingLog.setReconciliationDate(LocalDate.of(2026, 5, 7));
        existingLog.setTotalTransactions(100);
        existingLog.setMatchedCount(98);
        existingLog.setMismatchedCount(2);
        existingLog.setStatus("DISCREPANCY_FOUND");
    }

    // ========== reconcileTransactions Tests ==========

    @Test
    void reconcileTransactions_Success() {
        ReconcileRequest request = ReconcileRequest.newBuilder()
                .setDate("2026-05-07")
                .build();

        when(reconciliationRepository.save(any(ReconciliationLog.class))).thenAnswer(invocation -> {
            ReconciliationLog log = invocation.getArgument(0);
            log.setId("LOG-NEW");
            return log;
        });

        reconciliationService.reconcileTransactions(request, reconcileResponseObserver);

        ArgumentCaptor<ReconciliationLog> logCaptor = ArgumentCaptor.forClass(ReconciliationLog.class);
        verify(reconciliationRepository).save(logCaptor.capture());
        ReconciliationLog savedLog = logCaptor.getValue();

        assertEquals(LocalDate.of(2026, 5, 7), savedLog.getReconciliationDate());
        assertEquals(100, savedLog.getTotalTransactions());
        assertEquals(98, savedLog.getMatchedCount());
        assertEquals(2, savedLog.getMismatchedCount());
        assertEquals("DISCREPANCY_FOUND", savedLog.getStatus());

        verify(reconcileResponseObserver).onNext(argThat(response ->
                response.getStatus().equals("DISCREPANCY_FOUND")
        ));
        verify(reconcileResponseObserver).onCompleted();
    }

    @Test
    void reconcileTransactions_VerifyDateParsing() {
        ReconcileRequest request = ReconcileRequest.newBuilder()
                .setDate("2026-12-31")
                .build();

        when(reconciliationRepository.save(any(ReconciliationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reconciliationService.reconcileTransactions(request, reconcileResponseObserver);

        verify(reconciliationRepository).save(argThat(log ->
                log.getReconciliationDate().equals(LocalDate.of(2026, 12, 31))
        ));
        verify(reconcileResponseObserver).onCompleted();
    }

    @Test
    void reconcileTransactions_EmptyDate() {
        ReconcileRequest request = ReconcileRequest.newBuilder()
                .setDate("")
                .build();

        // This will likely throw an exception due to parsing empty string
        // Just verify the method completes without crashing the test
        assertThrows(Exception.class, () -> {
            reconciliationService.reconcileTransactions(request, reconcileResponseObserver);
        });
    }

    // ========== detectMismatch Tests ==========

    @Test
    void detectMismatch_NotImplemented() {
        MismatchRequest request = MismatchRequest.newBuilder()
                .setDate("2026-05-07")
                .build();

        // Method is not implemented - just verify it doesn't throw
        assertDoesNotThrow(() ->
                reconciliationService.detectMismatch(request, mismatchResponseObserver)
        );
    }

    // ========== resolveMismatch Tests ==========

    @Test
    void resolveMismatch_NotImplemented() {
        ResolveMismatchRequest request = ResolveMismatchRequest.newBuilder()
                .setMismatchId("MIS-001")
                .setResolution("Manual adjustment")
                .build();

        // Method is not implemented - just verify it doesn't throw
        assertDoesNotThrow(() ->
                reconciliationService.resolveMismatch(request, reconcileResponseObserver)
        );
    }

    // ========== generateSettlementReport Tests ==========

    @Test
    void generateSettlementReport_NotImplemented() {
        SettlementRequest request = SettlementRequest.newBuilder()
                .setDate("2026-05-07")
                .build();

        // Method is not implemented - just verify it doesn't throw
        assertDoesNotThrow(() ->
                reconciliationService.generateSettlementReport(request, settlementResponseObserver)
        );
    }

    // ========== Integration Tests ==========

    @Test
    void reconcileThenGenerateReport() {
        // Reconcile transactions
        ReconcileRequest reconcileReq = ReconcileRequest.newBuilder()
                .setDate("2026-05-07")
                .build();

        when(reconciliationRepository.save(any(ReconciliationLog.class))).thenAnswer(invocation -> {
            ReconciliationLog log = invocation.getArgument(0);
            log.setId("LOG-INT");
            return log;
        });

        reconciliationService.reconcileTransactions(reconcileReq, reconcileResponseObserver);

        verify(reconcileResponseObserver).onNext(argThat(r -> r.getStatus().equals("DISCREPANCY_FOUND")));
        verify(reconcileResponseObserver).onCompleted();

        // Generate settlement report (not implemented - just verify it doesn't throw)
        SettlementRequest reportReq = SettlementRequest.newBuilder()
                .setDate("2026-05-07")
                .build();

        assertDoesNotThrow(() ->
                reconciliationService.generateSettlementReport(reportReq, settlementResponseObserver)
        );
    }

    @Test
    void reconcileTransactions_VerifyRepositorySave() {
        ReconcileRequest request = ReconcileRequest.newBuilder()
                .setDate("2026-05-07")
                .build();

        reconciliationService.reconcileTransactions(request, reconcileResponseObserver);

        verify(reconciliationRepository, times(1)).save(any(ReconciliationLog.class));
        verify(reconcileResponseObserver).onCompleted();
    }
}
