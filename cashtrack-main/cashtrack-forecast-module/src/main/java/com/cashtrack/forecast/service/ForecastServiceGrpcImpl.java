package com.cashtrack.forecast.service;

import com.cashtrack.api.*;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.concurrent.TimeUnit;

@GrpcService
public class ForecastServiceGrpcImpl extends ForecastServiceGrpc.ForecastServiceImplBase {
    @GrpcClient("machineService")
    private MachineServiceGrpc.MachineServiceBlockingStub machineServiceBlockingStub;

    @Override
    public void forecastCashDemand(ForecastRequest request, StreamObserver<ForecastResponse> responseObserver) {
        ATMCashResponse atmCash;
        try {
            atmCash = machineServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).getATMCashStatus(
                    ATMIdRequest.newBuilder().setAtmId(request.getAtmId()).build());
        } catch (Exception ex) {
            responseObserver.onNext(ForecastResponse.newBuilder()
                    .setForecastData("{\"atmId\":\"" + request.getAtmId() + "\",\"error\":\"machine_service_unavailable\"}")
                    .build());
            responseObserver.onCompleted();
            return;
        }
        responseObserver.onNext(ForecastResponse.newBuilder()
                .setForecastData("{\"atmId\":\"" + request.getAtmId() + "\",\"recommendedLoad\":" + Math.max(0, 20000 - atmCash.getCashAvailable()) + "}")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void recommendCashLoading(RecommendCashRequest request, StreamObserver<ForecastResponse> responseObserver) {
        ATMCashResponse atmCash;
        try {
            atmCash = machineServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).getATMCashStatus(
                    ATMIdRequest.newBuilder().setAtmId(request.getAtmId()).build());
        } catch (Exception ex) {
            responseObserver.onNext(ForecastResponse.newBuilder()
                    .setForecastData("{\"atmId\":\"" + request.getAtmId() + "\",\"error\":\"machine_service_unavailable\"}")
                    .build());
            responseObserver.onCompleted();
            return;
        }
        responseObserver.onNext(ForecastResponse.newBuilder()
                .setForecastData("{\"atmId\":\"" + request.getAtmId() + "\",\"loadNow\":" + (atmCash.getCashAvailable() < 5000) + "}")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void optimizeATMDistribution(OptimizeDistributionRequest request, StreamObserver<ForecastResponse> responseObserver) {
        responseObserver.onNext(ForecastResponse.newBuilder()
                .setForecastData("{\"region\":\"" + request.getRegion() + "\",\"strategy\":\"rebalance\"}")
                .build());
        responseObserver.onCompleted();
    }
}