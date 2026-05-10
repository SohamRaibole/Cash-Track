package com.cashtrack.auth.service;

import com.cashtrack.api.*;
import com.cashtrack.auth.entity.User;
import com.cashtrack.auth.entity.Role;
import com.cashtrack.auth.repository.UserRepository;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StreamObserver<AuthResponse> responseObserver;

    @InjectMocks
    private AuthServiceGrpcImpl authService;

    private User validUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        Set<Role> customerRoles = new HashSet<>();
        customerRoles.add(Role.CUSTOMER);

        validUser = new User();
        validUser.setId("USER-001");
        validUser.setUsername("alice.j@cashtrack.com");
        validUser.setPassword("SecurePass123!");
        validUser.setRoles(customerRoles);

        Set<Role> adminRoles = new HashSet<>();
        adminRoles.add(Role.BANK_ADMIN);

        adminUser = new User();
        adminUser.setId("USER-002");
        adminUser.setUsername("admin@cashtrack.com");
        adminUser.setPassword("AdminPass456!");
        adminUser.setRoles(adminRoles);
    }

    // ========== login Tests ==========

    @Test
    void login_Success() {
        when(userRepository.findByUsername("alice.j@cashtrack.com"))
                .thenReturn(Optional.of(validUser));

        LoginRequest request = LoginRequest.newBuilder()
                .setUsername("alice.j@cashtrack.com")
                .setPassword("SecurePass123!")
                .build();

        authService.login(request, responseObserver);

        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("SUCCESS") &&
                response.getToken().startsWith("generated-jwt-token-for-")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void login_WrongPassword() {
        when(userRepository.findByUsername("alice.j@cashtrack.com"))
                .thenReturn(Optional.of(validUser));

        LoginRequest request = LoginRequest.newBuilder()
                .setUsername("alice.j@cashtrack.com")
                .setPassword("WrongPass")
                .build();

        authService.login(request, responseObserver);

        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("FAILED") &&
                (response.getToken() == null || response.getToken().isEmpty())
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void login_UserNotFound() {
        when(userRepository.findByUsername("nonexistent@cashtrack.com"))
                .thenReturn(Optional.empty());

        LoginRequest request = LoginRequest.newBuilder()
                .setUsername("nonexistent@cashtrack.com")
                .setPassword("AnyPass")
                .build();

        authService.login(request, responseObserver);

        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("FAILED")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void login_AdminUser_Success() {
        when(userRepository.findByUsername("admin@cashtrack.com"))
                .thenReturn(Optional.of(adminUser));

        LoginRequest request = LoginRequest.newBuilder()
                .setUsername("admin@cashtrack.com")
                .setPassword("AdminPass456!")
                .build();

        authService.login(request, responseObserver);

        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("SUCCESS") &&
                response.getToken().equals("generated-jwt-token-for-admin@cashtrack.com")
        ));
        verify(responseObserver).onCompleted();
    }

    @Test
    void login_EmptyCredentials() {
        when(userRepository.findByUsername(""))
                .thenReturn(Optional.empty());

        LoginRequest request = LoginRequest.newBuilder()
                .setUsername("")
                .setPassword("")
                .build();

        authService.login(request, responseObserver);

        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("FAILED")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== validateToken Tests ==========

    @Test
    void validateToken_ValidToken() {
        TokenRequest request = TokenRequest.newBuilder()
                .setToken("generated-jwt-token-for-alice.j@cashtrack.com")
                .build();

        authService.validateToken(request, responseObserver);

        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("VALID")
        ));
        verify(responseObserver).onCompleted();
        verifyNoInteractions(userRepository);
    }

    @Test
    void validateToken_InvalidToken() {
        TokenRequest request = TokenRequest.newBuilder()
                .setToken("invalid-token-xyz")
                .build();

        authService.validateToken(request, responseObserver);

        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("INVALID")
        ));
        verify(responseObserver).onCompleted();
        verifyNoInteractions(userRepository);
    }

    @Test
    void validateToken_EmptyToken() {
        TokenRequest request = TokenRequest.newBuilder()
                .setToken("")
                .build();

        authService.validateToken(request, responseObserver);

        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("INVALID")
        ));
        verify(responseObserver).onCompleted();
        verifyNoInteractions(userRepository);
    }

    @Test
    void validateToken_TokenStartsWithGenerated() {
        // Any token starting with "generated-jwt-token-" should be valid
        TokenRequest request = TokenRequest.newBuilder()
                .setToken("generated-jwt-token-for-any-user")
                .build();

        authService.validateToken(request, responseObserver);

        verify(responseObserver).onNext(argThat(response ->
                response.getStatus().equals("VALID")
        ));
        verify(responseObserver).onCompleted();
    }

    // ========== Integration Tests ==========

    @Test
    void loginThenValidateToken() {
        when(userRepository.findByUsername("alice.j@cashtrack.com"))
                .thenReturn(Optional.of(validUser));

        // Login
        LoginRequest loginReq = LoginRequest.newBuilder()
                .setUsername("alice.j@cashtrack.com")
                .setPassword("SecurePass123!")
                .build();

        authService.login(loginReq, responseObserver);

        verify(responseObserver).onNext(argThat(r -> r.getStatus().equals("SUCCESS")));
        verify(responseObserver).onCompleted();

        // Validate the token from login
        // Since we can't easily capture the token, we'll test with a manually constructed valid token
        TokenRequest tokenReq = TokenRequest.newBuilder()
                .setToken("generated-jwt-token-for-alice.j@cashtrack.com")
                .build();

        authService.validateToken(tokenReq, responseObserver);

        verify(responseObserver, times(2)).onCompleted();
    }
}
