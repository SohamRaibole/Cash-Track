package com.cashtrack.session.service;

import com.cashtrack.api.*;
import com.cashtrack.session.entity.AtmSession;
import com.cashtrack.session.repository.SessionRepository;
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
public class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private StreamObserver<SessionResponse> responseObserver;

    @InjectMocks
    private SessionServiceGrpcImpl sessionService;

    private AtmSession activeSession;
    private AtmSession expiredSession;

    @BeforeEach
    void setUp() {
        activeSession = new AtmSession();
        activeSession.setSessionId("SESS-001");
        activeSession.setAtmId("ATM-001");
        activeSession.setCardNumber("4532015112830366");
        activeSession.setStatus("AUTHENTICATED");
        activeSession.setToken("valid-token-123");
        activeSession.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        activeSession.setExpiresAt(LocalDateTime.now().plusMinutes(3));

        expiredSession = new AtmSession();
        expiredSession.setSessionId("SESS-002");
        expiredSession.setAtmId("ATM-001");
        expiredSession.setCardNumber("4532015112830366");
        expiredSession.setStatus("INITIATED");
        expiredSession.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        expiredSession.setExpiresAt(LocalDateTime.now().minusMinutes(5));
    }

    // ========== initiateSession Tests ==========

    @Test
    void initiateSession_Success() {
        InitiateSessionRequest request = InitiateSessionRequest.newBuilder()
                .setAtmId("ATM-001")
                .setCardNumber("4532015112830366")
                .build();

        when(sessionRepository.save(any(AtmSession.class))).thenAnswer(invocation -> {
            AtmSession session = invocation.getArgument(0);
            session.setSessionId("SESS-NEW");
            return session;
        });

        sessionService.initiateSession(request, responseObserver);

        ArgumentCaptor<AtmSession> sessionCaptor = ArgumentCaptor.forClass(AtmSession.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        AtmSession savedSession = sessionCaptor.getValue();

        assertEquals("ATM-001", savedSession.getAtmId());
        assertEquals("4532015112830366", savedSession.getCardNumber());
        assertEquals("INITIATED", savedSession.getStatus());

        verify(responseObserver).onNext(argThat(response ->
                response.getSessionId().equals("SESS-NEW") &&
                response.getStatus().equals("INITIATED") &&
                response.getToken().isEmpty()
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void initiateSession_VerifySessionSaved() {
        InitiateSessionRequest request = InitiateSessionRequest.newBuilder()
                .setAtmId("ATM-002")
                .setCardNumber("5555555555554444")
                .build();

        when(sessionRepository.save(any(AtmSession.class))).thenAnswer(invocation -> {
            AtmSession session = invocation.getArgument(0);
            // Simulate @PrePersist behavior
            if (session.getSessionId() == null) {
                session.setSessionId(java.util.UUID.randomUUID().toString());
            }
            return session;
        });

        sessionService.initiateSession(request, responseObserver);

        verify(sessionRepository).save(argThat(session ->
                session.getAtmId().equals("ATM-002") &&
                session.getCardNumber().equals("5555555555554444") &&
                session.getSessionId() != null
        ));
        verify(responseObserver).onNext(argThat(response ->
                response.getSessionId() != null &&
                response.getStatus().equals("INITIATED")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== validatePIN Tests ==========

    @Test
    void validatePIN_Success() {
        when(sessionRepository.findById("SESS-001")).thenReturn(Optional.of(activeSession));

        ValidatePINRequest request = ValidatePINRequest.newBuilder()
                .setSessionId("SESS-001")
                .setPin("1234")
                .build();

        sessionService.validatePIN(request, responseObserver);

        verify(sessionRepository).save(argThat(session ->
                session.getStatus().equals("AUTHENTICATED") &&
                session.getToken() != null &&
                !session.getToken().isEmpty()
        ));
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("AUTHENTICATED") &&
                !response.getToken().isEmpty()
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void validatePIN_EmptyPin_Failure() {
        when(sessionRepository.findById("SESS-001")).thenReturn(Optional.of(activeSession));

        ValidatePINRequest request = ValidatePINRequest.newBuilder()
                .setSessionId("SESS-001")
                .setPin("")
                .build();

        sessionService.validatePIN(request, responseObserver);

        verify(sessionRepository).save(argThat(session ->
                session.getStatus().equals("TERMINATED")
        ));
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("FAILED")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void validatePIN_SessionNotFound() {
        when(sessionRepository.findById("SESS-999")).thenReturn(Optional.empty());

        ValidatePINRequest request = ValidatePINRequest.newBuilder()
                .setSessionId("SESS-999")
                .setPin("1234")
                .build();

        sessionService.validatePIN(request, responseObserver);

        verify(sessionRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getSessionId().equals("SESS-999") &&
                response.getStatus().equals("NOT_FOUND")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void validatePIN_ExpiredSession() {
        when(sessionRepository.findById("SESS-002")).thenReturn(Optional.of(expiredSession));

        ValidatePINRequest request = ValidatePINRequest.newBuilder()
                .setSessionId("SESS-002")
                .setPin("1234")
                .build();

        sessionService.validatePIN(request, responseObserver);

        verify(sessionRepository).save(argThat(session ->
                session.getStatus().equals("TERMINATED")
        ));
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("FAILED")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== authenticateCard Tests ==========

    @Test
    void authenticateCard_Success() {
        AuthCardRequest request = AuthCardRequest.newBuilder()
                .setCardNumber("4532015112830366")
                .build();

        sessionService.authenticateCard(request, responseObserver);

        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("CARD_AUTHENTICATED") &&
                response.getSessionId().isEmpty()
        ));
        verify(responseObserver).onCompleted();
        verifyNoInteractions(sessionRepository);
    }

    // ========== refreshSession Tests ==========

    @Test
    void refreshSession_Success() {
        when(sessionRepository.findById("SESS-001")).thenReturn(Optional.of(activeSession));

        SessionIdRequest request = SessionIdRequest.newBuilder()
                .setSessionId("SESS-001")
                .build();

        sessionService.refreshSession(request, responseObserver);

        verify(sessionRepository).save(argThat(session ->
                session.getExpiresAt().isAfter(LocalDateTime.now())
        ));
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("REFRESHED") &&
                response.getToken().equals("valid-token-123")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void refreshSession_NotFound() {
        when(sessionRepository.findById("SESS-999")).thenReturn(Optional.empty());

        SessionIdRequest request = SessionIdRequest.newBuilder()
                .setSessionId("SESS-999")
                .build();

        sessionService.refreshSession(request, responseObserver);

        verify(sessionRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("FAILED")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void refreshSession_NotAuthenticated() {
        AtmSession unauthSession = new AtmSession();
        unauthSession.setSessionId("SESS-003");
        unauthSession.setStatus("INITIATED");
        unauthSession.setExpiresAt(LocalDateTime.now().plusMinutes(3));

        when(sessionRepository.findById("SESS-003")).thenReturn(Optional.of(unauthSession));

        SessionIdRequest request = SessionIdRequest.newBuilder()
                .setSessionId("SESS-003")
                .build();

        sessionService.refreshSession(request, responseObserver);

        verify(sessionRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("FAILED")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== terminateSession Tests ==========

    @Test
    void terminateSession_Success() {
        when(sessionRepository.findById("SESS-001")).thenReturn(Optional.of(activeSession));

        SessionIdRequest request = SessionIdRequest.newBuilder()
                .setSessionId("SESS-001")
                .build();

        sessionService.terminateSession(request, responseObserver);

        verify(sessionRepository).save(argThat(session ->
                session.getStatus().equals("TERMINATED") &&
                (session.getToken() == null || session.getToken().isEmpty())
        ));
        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("TERMINATED") &&
                response.getToken().isEmpty()
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void terminateSession_NotFound() {
        when(sessionRepository.findById("SESS-999")).thenReturn(Optional.empty());

        SessionIdRequest request = SessionIdRequest.newBuilder()
                .setSessionId("SESS-999")
                .build();

        sessionService.terminateSession(request, responseObserver);

        verify(sessionRepository, never()).save(any());
        verify(responseObserver).onNext(argThat(response ->
                response.getSessionId().equals("SESS-999") &&
                response.getStatus().equals("NOT_FOUND")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void terminateSession_VerifyTokenCleared() {
        activeSession.setToken("some-token");
        when(sessionRepository.findById("SESS-001")).thenReturn(Optional.of(activeSession));

        SessionIdRequest request = SessionIdRequest.newBuilder()
                .setSessionId("SESS-001")
                .build();

        sessionService.terminateSession(request, responseObserver);

        verify(sessionRepository).save(argThat(session ->
                session.getToken() == null || session.getToken().isEmpty()
        ));
        verify(responseObserver).onCompleted();
    }
}
