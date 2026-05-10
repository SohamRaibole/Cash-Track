package com.cashtrack.fraud.service;

import com.cashtrack.api.*;
import com.cashtrack.fraud.entity.FraudAlert;
import com.cashtrack.fraud.repository.FraudRepository;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FraudServiceTest {

    @Mock
    private FraudRepository fraudRepository;

    @Mock
    private StreamObserver<FraudResponse> fraudResponseObserver;

    @Mock
    private StreamObserver<FraudReportResponse> reportResponseObserver;

    @InjectMocks
    private FraudServiceGrpcImpl fraudService;

    private FraudAlert existingAlert;

    @BeforeEach
    void setUp() {
        existingAlert = new FraudAlert();
        existingAlert.setId("ALERT-001");
        existingAlert.setAccountId("ACC-001");
        existingAlert.setTransactionId("TXN-W-001");
        existingAlert.setAlertType("RAPID_WITHDRAWAL");
        existingAlert.setSeverity("HIGH");
        existingAlert.setTimestamp(LocalDateTime.now());
    }

    // ========== detectFraudulentTransaction Tests ==========

    @Test
    void detectFraudulentTransaction_ReturnsSafe() {
        FraudTransactionIdRequest request = FraudTransactionIdRequest.newBuilder()
                .setTransactionId("TXN-W-001")
                .build();

        fraudService.detectFraudulentTransaction(request, fraudResponseObserver);

        verify(fraudResponseObserver).onNext(argThat(response ->
                response.getStatus().equals("SAFE")
        ));
        verify(fraudResponseObserver).onCompleted();
        verifyNoInteractions(fraudRepository);
    }

    @Test
    void detectFraudulentTransaction_EmptyTransactionId() {
        FraudTransactionIdRequest request = FraudTransactionIdRequest.newBuilder()
                .setTransactionId("")
                .build();

        fraudService.detectFraudulentTransaction(request, fraudResponseObserver);

        verify(fraudResponseObserver).onNext(argThat(response ->
                response.getStatus().equals("SAFE")
        ));
        verify(fraudResponseObserver).onCompleted();
        verifyNoInteractions(fraudRepository);
    }

    // ========== flagSuspiciousActivity Tests ==========

    @Test
    void flagSuspiciousActivity_Success() {
        SuspiciousActivityRequest request = SuspiciousActivityRequest.newBuilder()
                .setAccountId("ACC-001")
                .setActivityDetails("5 withdrawals in 10 minutes")
                .build();

        when(fraudRepository.save(any(FraudAlert.class))).thenAnswer(invocation -> {
            FraudAlert alert = invocation.getArgument(0);
            alert.setId("ALERT-NEW");
            return alert;
        });

        fraudService.flagSuspiciousActivity(request, fraudResponseObserver);

        ArgumentCaptor<FraudAlert> alertCaptor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(fraudRepository).save(alertCaptor.capture());
        FraudAlert savedAlert = alertCaptor.getValue();

        assertEquals("ACC-001", savedAlert.getAccountId());
        assertEquals("MANUAL_FLAG", savedAlert.getAlertType());
        assertEquals("HIGH", savedAlert.getSeverity());
        assertNotNull(savedAlert.getTimestamp());

        verify(fraudResponseObserver).onNext(argThat(response ->
                response.getStatus().equals("FLAGGED")
        ));
        verify(fraudResponseObserver).onCompleted();
    }

    @Test
    void flagSuspiciousActivity_EmptyAccountId() {
        SuspiciousActivityRequest request = SuspiciousActivityRequest.newBuilder()
                .setAccountId("")
                .setActivityDetails("Suspicious activity detected")
                .build();

        when(fraudRepository.save(any(FraudAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fraudService.flagSuspiciousActivity(request, fraudResponseObserver);

        verify(fraudRepository).save(argThat(alert ->
                alert.getAccountId().equals("") &&
                alert.getAlertType().equals("MANUAL_FLAG")
        ));
        verify(fraudResponseObserver).onCompleted();
    }

    @Test
    void flagSuspiciousActivity_VerifyTimestamp() {
        SuspiciousActivityRequest request = SuspiciousActivityRequest.newBuilder()
                .setAccountId("ACC-002")
                .setActivityDetails("Multiple failed PIN attempts")
                .build();

        when(fraudRepository.save(any(FraudAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fraudService.flagSuspiciousActivity(request, fraudResponseObserver);

        verify(fraudRepository).save(argThat(alert ->
                alert.getTimestamp() != null &&
                alert.getTimestamp().isBefore(LocalDateTime.now().plusSeconds(1))
        ));
        verify(fraudResponseObserver).onCompleted();
    }

    // ========== analyzeTransactionPatterns Tests ==========

    @Test
    void analyzeTransactionPatterns_NotImplemented() {
        FraudAccountIdRequest request = FraudAccountIdRequest.newBuilder()
                .setAccountId("ACC-001")
                .build();

        // Method is not implemented - just verify it doesn't throw
        assertDoesNotThrow(() ->
                fraudService.analyzeTransactionPatterns(request, fraudResponseObserver)
        );
    }

    // ========== getFraudReports Tests ==========

    @Test
    void getFraudReports_NotImplemented() {
        FraudReportRequest request = FraudReportRequest.newBuilder()
                .setDate("2026-05-07")
                .build();

        // Method is not implemented yet - just verify it doesn't throw
        assertDoesNotThrow(() ->
                fraudService.getFraudReports(request, reportResponseObserver)
        );
    }

    @Test
    void getFraudReports_EmptyDate() {
        FraudReportRequest request = FraudReportRequest.newBuilder()
                .setDate("")
                .build();

        // Method is not implemented yet - just verify it doesn't throw
        assertDoesNotThrow(() ->
                fraudService.getFraudReports(request, reportResponseObserver)
        );
    }

    // ========== Multiple Flag Tests ==========

    @Test
    void flagMultipleSuspiciousActivities() {
        // Flag first activity
        SuspiciousActivityRequest request1 = SuspiciousActivityRequest.newBuilder()
                .setAccountId("ACC-001")
                .setActivityDetails("Rapid withdrawals")
                .build();

        when(fraudRepository.save(any(FraudAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fraudService.flagSuspiciousActivity(request1, fraudResponseObserver);

        // Flag second activity
        SuspiciousActivityRequest request2 = SuspiciousActivityRequest.newBuilder()
                .setAccountId("ACC-001")
                .setActivityDetails("Location anomaly")
                .build();

        fraudService.flagSuspiciousActivity(request2, fraudResponseObserver);

        verify(fraudRepository, times(2)).save(any(FraudAlert.class));
        verify(fraudResponseObserver, times(2)).onCompleted();
    }
}
