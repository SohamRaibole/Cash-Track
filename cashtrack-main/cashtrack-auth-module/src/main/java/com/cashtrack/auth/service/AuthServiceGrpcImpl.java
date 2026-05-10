package com.cashtrack.auth.service;

import com.cashtrack.api.*;
import com.cashtrack.auth.entity.User;
import com.cashtrack.auth.repository.UserRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@GrpcService
public class AuthServiceGrpcImpl extends AuthServiceGrpc.AuthServiceImplBase {

    private static final int TOKEN_TTL_MINUTES = 30;

    @Autowired
    private UserRepository userRepository;

    @GrpcClient("notificationService")
    private NotificationServiceGrpc.NotificationServiceBlockingStub notificationServiceBlockingStub;

    private final ConcurrentMap<String, TokenSession> activeSessions = new ConcurrentHashMap<>();

    @Override
    public void login(LoginRequest request, StreamObserver<AuthResponse> responseObserver) {
        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());
        
        if (userOpt.isPresent() && userOpt.get().getPassword().equals(request.getPassword())) {
            String token = generateToken(userOpt.get().getUsername());
            activeSessions.put(token, new TokenSession(userOpt.get().getUsername(), LocalDateTime.now().plusMinutes(TOKEN_TTL_MINUTES)));

            notifyLogin(userOpt.get().getUsername());

            responseObserver.onNext(AuthResponse.newBuilder()
                    .setToken(token)
                    .setStatus("SUCCESS")
                    .build());
        } else {
            responseObserver.onNext(AuthResponse.newBuilder()
                    .setStatus("FAILED")
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void validateToken(TokenRequest request, StreamObserver<AuthResponse> responseObserver) {
        boolean isValid = isTokenActive(request.getToken());
        responseObserver.onNext(AuthResponse.newBuilder()
                .setStatus(isValid ? "VALID" : "INVALID")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void refreshToken(TokenRequest request, StreamObserver<AuthResponse> responseObserver) {
        TokenSession session = activeSessions.remove(request.getToken());
        boolean isValid = session != null && session.expiresAt().isAfter(LocalDateTime.now());
        String refreshedToken = "";
        if (isValid) {
            refreshedToken = generateToken(session.username());
            activeSessions.put(refreshedToken, new TokenSession(session.username(), LocalDateTime.now().plusMinutes(TOKEN_TTL_MINUTES)));
        }
        responseObserver.onNext(AuthResponse.newBuilder()
                .setToken(refreshedToken)
                .setStatus(isValid ? "REFRESHED" : "INVALID")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void logout(TokenRequest request, StreamObserver<AuthResponse> responseObserver) {
        boolean removed = activeSessions.remove(request.getToken()) != null;
        responseObserver.onNext(AuthResponse.newBuilder()
                .setStatus(removed ? "LOGGED_OUT" : "INVALID")
                .build());
        responseObserver.onCompleted();
    }

    private boolean isTokenActive(String token) {
        TokenSession session = activeSessions.get(token);
        if (session == null) {
            return false;
        }
        if (session.expiresAt().isBefore(LocalDateTime.now())) {
            activeSessions.remove(token);
            return false;
        }
        return true;
    }

    private String generateToken(String username) {
        return "generated-jwt-token-for-" + username + "-" + UUID.randomUUID();
    }

    private void notifyLogin(String username) {
        try {
            notificationServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).sendTransactionAlert(TransactionAlertRequest.newBuilder()
                    .setAccountId(username)
                    .setTransactionDetails("Successful login")
                    .build());
        } catch (Exception ignored) {
            // Notification failure should not fail login.
        }
    }

    private record TokenSession(String username, LocalDateTime expiresAt) {}
}