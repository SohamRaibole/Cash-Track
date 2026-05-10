package com.cashtrack.forecast.service;

import com.cashtrack.api.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ForecastServiceTest {

    @Mock
    private StreamObserver<ForecastResponse> forecastResponseObserver;

    @InjectMocks
    private ForecastServiceGrpcImpl forecastService;

    // ========== forecastCashDemand Tests ==========

    @Test
    void forecastCashDemand_Success() {
        ForecastRequest request = ForecastRequest.newBuilder()
                .setAtmId("ATM-001")
                .build();

        forecastService.forecastCashDemand(request, forecastResponseObserver);

        verify(forecastResponseObserver).onNext(argThat(response ->
                response.getForecastData().equals("{}")
        ));
        verify(forecastResponseObserver).onCompleted();
    }

    @Test
    void forecastCashDemand_NotFound() {
        ForecastRequest request = ForecastRequest.newBuilder()
                .setAtmId("ATM-999")
                .build();

        forecastService.forecastCashDemand(request, forecastResponseObserver);

        verify(forecastResponseObserver).onNext(argThat(response ->
                response.getForecastData().equals("{}")
        ));
        verify(forecastResponseObserver).onCompleted();
    }

    @Test
    void forecastCashDemand_EmptyAtmId() {
        ForecastRequest request = ForecastRequest.newBuilder()
                .setAtmId("")
                .build();

        forecastService.forecastCashDemand(request, forecastResponseObserver);

        verify(forecastResponseObserver).onNext(argThat(response ->
                response.getForecastData() != null
        ));
        verify(forecastResponseObserver).onCompleted();
    }

    // ========== recommendCashLoading Tests ==========

    @Test
    void recommendCashLoading_NotImplemented() {
        RecommendCashRequest request = RecommendCashRequest.newBuilder()
                .setAtmId("ATM-001")
                .build();

        // Method is not implemented - just verify it doesn't throw
        assertDoesNotThrow(() ->
                forecastService.recommendCashLoading(request, forecastResponseObserver)
        );
    }

    // ========== optimizeATMDistribution Tests ==========

    @Test
    void optimizeATMDistribution_NotImplemented() {
        OptimizeDistributionRequest request = OptimizeDistributionRequest.newBuilder()
                .setRegion("New York Downtown")
                .build();

        // Method is not implemented - just verify it doesn't throw
        assertDoesNotThrow(() ->
                forecastService.optimizeATMDistribution(request, forecastResponseObserver)
        );
    }

    // ========== Multiple Forecast Calls ==========

    @Test
    void multipleForecastCalls() {
        // forecastCashDemand (implemented)
        ForecastRequest req1 = ForecastRequest.newBuilder()
                .setAtmId("ATM-001")
                .build();
        forecastService.forecastCashDemand(req1, forecastResponseObserver);

        // recommendCashLoading (not implemented)
        RecommendCashRequest req2 = RecommendCashRequest.newBuilder()
                .setAtmId("ATM-001")
                .build();
        assertDoesNotThrow(() ->
                forecastService.recommendCashLoading(req2, forecastResponseObserver)
        );

        // optimizeATMDistribution (not implemented)
        OptimizeDistributionRequest req3 = OptimizeDistributionRequest.newBuilder()
                .setRegion("New York Downtown")
                .build();
        assertDoesNotThrow(() ->
                forecastService.optimizeATMDistribution(req3, forecastResponseObserver)
        );

        // Only forecastCashDemand should call onCompleted()
        verify(forecastResponseObserver, times(1)).onCompleted();
    }

    // ========== Verify Response Data ==========

    @Test
    void forecastCashDemand_VerifyEmptyJson() {
        ForecastRequest request = ForecastRequest.newBuilder()
                .setAtmId("ATM-001")
                .build();

        forecastService.forecastCashDemand(request, forecastResponseObserver);

        verify(forecastResponseObserver).onNext(argThat(response ->
                response.getForecastData().equals("{}")
        ));
        verify(forecastResponseObserver).onCompleted();
    }
}
