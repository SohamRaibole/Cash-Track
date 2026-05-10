package com.cashtrack.analytics.service;

import com.cashtrack.api.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AnalyticsServiceTest {

    @Mock
    private StreamObserver<AnalyticsResponse> analyticsResponseObserver;

    @InjectMocks
    private AnalyticsServiceGrpcImpl analyticsService;

    // ========== getTransactionAnalytics Tests ==========

    @Test
    void getTransactionAnalytics_Success() {
        AnalyticsRequest request = AnalyticsRequest.newBuilder()
                .setDateRange("2026-05-01 to 2026-05-07")
                .build();

        analyticsService.getTransactionAnalytics(request, analyticsResponseObserver);

        verify(analyticsResponseObserver).onNext(argThat(response ->
                response.getData().equals("{}")
        ));
        verify(analyticsResponseObserver).onCompleted();
    }

    @Test
    void getTransactionAnalytics_EmptyDateRange() {
        AnalyticsRequest request = AnalyticsRequest.newBuilder()
                .setDateRange("")
                .build();

        analyticsService.getTransactionAnalytics(request, analyticsResponseObserver);

        verify(analyticsResponseObserver).onNext(argThat(response ->
                response.getData() != null
        ));
        verify(analyticsResponseObserver).onCompleted();
    }

    @Test
    void getTransactionAnalytics_VerifyEmptyJson() {
        AnalyticsRequest request = AnalyticsRequest.newBuilder()
                .setDateRange("2026-05-01 to 2026-05-31")
                .build();

        analyticsService.getTransactionAnalytics(request, analyticsResponseObserver);

        verify(analyticsResponseObserver).onNext(argThat(response ->
                response.getData().equals("{}")
        ));
        verify(analyticsResponseObserver).onCompleted();
    }

    // ========== getATMUsageStats Tests ==========

    @Test
    void getATMUsageStats_NotImplemented() {
        AnalyticsATMIdRequest request = AnalyticsATMIdRequest.newBuilder()
                .setAtmId("ATM-001")
                .build();

        // Method is not implemented - just verify it doesn't throw
        assertDoesNotThrow(() ->
                analyticsService.getATMUsageStats(request, analyticsResponseObserver)
        );
    }

    // ========== getRevenueReport Tests ==========

    @Test
    void getRevenueReport_NotImplemented() {
        RevenueRequest request = RevenueRequest.newBuilder()
                .setDateRange("2026-05-01 to 2026-05-07")
                .build();

        // Method is not implemented - just verify it doesn't throw
        assertDoesNotThrow(() ->
                analyticsService.getRevenueReport(request, analyticsResponseObserver)
        );
    }

    // ========== getCustomerInsights Tests ==========

    @Test
    void getCustomerInsights_NotImplemented() {
        AnalyticsAccountIdRequest request = AnalyticsAccountIdRequest.newBuilder()
                .setAccountId("ACC-001")
                .build();

        // Method is not implemented - just verify it doesn't throw
        assertDoesNotThrow(() ->
                analyticsService.getCustomerInsights(request, analyticsResponseObserver)
        );
    }

    // ========== Multiple Analytics Calls ==========

    @Test
    void multipleAnalyticsCalls() {
        // Call getTransactionAnalytics
        AnalyticsRequest req1 = AnalyticsRequest.newBuilder()
                .setDateRange("2026-05-01 to 2026-05-07")
                .build();
        analyticsService.getTransactionAnalytics(req1, analyticsResponseObserver);

        // Call getATMUsageStats (not implemented)
        AnalyticsATMIdRequest req2 = AnalyticsATMIdRequest.newBuilder()
                .setAtmId("ATM-001")
                .build();
        assertDoesNotThrow(() ->
                analyticsService.getATMUsageStats(req2, analyticsResponseObserver)
        );

        // Call getRevenueReport (not implemented)
        RevenueRequest req3 = RevenueRequest.newBuilder()
                .setDateRange("2026-05-01 to 2026-05-07")
                .build();
        assertDoesNotThrow(() ->
                analyticsService.getRevenueReport(req3, analyticsResponseObserver)
        );

        // Call getCustomerInsights (not implemented)
        AnalyticsAccountIdRequest req4 = AnalyticsAccountIdRequest.newBuilder()
                .setAccountId("ACC-001")
                .build();
        assertDoesNotThrow(() ->
                analyticsService.getCustomerInsights(req4, analyticsResponseObserver)
        );

        // Only getTransactionAnalytics should call onCompleted()
        verify(analyticsResponseObserver, times(1)).onCompleted();
    }
}
