package com.cashtrack.account.service;

import com.cashtrack.api.*;
import com.cashtrack.account.entity.Account;
import com.cashtrack.account.entity.Card;
import com.cashtrack.account.repository.AccountRepository;
import com.cashtrack.account.repository.CardRepository;
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
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private StreamObserver<AccountResponse> accountResponseObserver;

    @Mock
    private StreamObserver<AccountSummaryResponse> summaryResponseObserver;

    @InjectMocks
    private AccountServiceGrpcImpl accountService;

    private Account activeAccount;
    private Card activeCard;

    @BeforeEach
    void setUp() {
        activeAccount = new Account();
        activeAccount.setAccountId("ACC-001");
        activeAccount.setCustomerName("John Doe");
        activeAccount.setKycDetails("PASSPORT:AB1234567");
        activeAccount.setBalance(5000.0);
        activeAccount.setStatus("ACTIVE");

        activeCard = new Card();
        activeCard.setCardNumber("4532015112830366");
        activeCard.setAccount(activeAccount);
        activeCard.setStatus("ACTIVE");
        activeCard.setPinHash("HASH123");
    }

    // ========== createAccount Tests ==========

    @Test
    void createAccount_Success() {
        CreateAccountRequest request = CreateAccountRequest.newBuilder()
                .setCustomerName("John Doe")
                .setKycDetails("PASSPORT:AB1234567")
                .setInitialDeposit(5000.0)
                .build();

        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account acc = invocation.getArgument(0);
            acc.setAccountId("ACC-001");
            return acc;
        });

        accountService.createAccount(request, accountResponseObserver);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, times(1)).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();

        assertEquals("John Doe", savedAccount.getCustomerName());
        assertEquals("PASSPORT:AB1234567", savedAccount.getKycDetails());
        assertEquals(5000.0, savedAccount.getBalance());
        assertEquals("ACTIVE", savedAccount.getStatus());

        verify(accountResponseObserver).onNext(argThat(response ->
                response.getAccountId().equals("ACC-001") &&
                response.getStatus().equals("SUCCESS") &&
                response.getMessage().equals("Account created successfully")
        ));
        verify(accountResponseObserver).onCompleted();
    }

    @Test
    void createAccount_WithZeroInitialDeposit() {
        CreateAccountRequest request = CreateAccountRequest.newBuilder()
                .setCustomerName("Jane Smith")
                .setKycDetails("DRIVING_LICENSE:DL987654")
                .setInitialDeposit(0.0)
                .build();

        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account acc = invocation.getArgument(0);
            acc.setAccountId("ACC-002");
            return acc;
        });

        accountService.createAccount(request, accountResponseObserver);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertEquals(0.0, accountCaptor.getValue().getBalance());
        verify(accountResponseObserver).onCompleted();
    }

    // ========== updateAccountDetails Tests ==========

    @Test
    void updateAccountDetails_Success() {
        when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));

        UpdateAccountRequest request = UpdateAccountRequest.newBuilder()
                .setAccountId("ACC-001")
                .setCustomerName("John Updated")
                .setKycDetails("PASSPORT:AB1234567, PHONE:+1-555-0123")
                .build();

        accountService.updateAccountDetails(request, accountResponseObserver);

        verify(accountRepository).save(argThat(account ->
                account.getCustomerName().equals("John Updated") &&
                account.getKycDetails().equals("PASSPORT:AB1234567, PHONE:+1-555-0123")
        ));
        verify(accountResponseObserver).onNext(argThat(response ->
                response.getAccountId().equals("ACC-001") &&
                response.getStatus().equals("SUCCESS")
        ));
        verify(accountResponseObserver).onCompleted();
    }

    @Test
    void updateAccountDetails_AccountNotFound() {
        when(accountRepository.findById("ACC-999")).thenReturn(Optional.empty());

        UpdateAccountRequest request = UpdateAccountRequest.newBuilder()
                .setAccountId("ACC-999")
                .setCustomerName("Nonexistent")
                .setKycDetails("N/A")
                .build();

        accountService.updateAccountDetails(request, accountResponseObserver);

        verify(accountRepository, never()).save(any());
        verify(accountResponseObserver).onNext(argThat(response ->
                response.getAccountId().equals("ACC-999") &&
                response.getStatus().equals("FAILED") &&
                response.getMessage().contains("not found")
        ));
        verify(accountResponseObserver).onCompleted();
    }

    // ========== deactivateAccount Tests ==========

    @Test
    void deactivateAccount_Success() {
        when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));

        AccountIdRequest request = AccountIdRequest.newBuilder()
                .setAccountId("ACC-001")
                .build();

        accountService.deactivateAccount(request, accountResponseObserver);

        verify(accountRepository).save(argThat(account ->
                account.getStatus().equals("INACTIVE")
        ));
        verify(accountResponseObserver).onNext(argThat(response ->
                response.getStatus().equals("SUCCESS") &&
                response.getMessage().contains("deactivated")
        ));
        verify(accountResponseObserver).onCompleted();
    }

    @Test
    void deactivateAccount_AccountNotFound() {
        when(accountRepository.findById("ACC-999")).thenReturn(Optional.empty());

        AccountIdRequest request = AccountIdRequest.newBuilder()
                .setAccountId("ACC-999")
                .build();

        accountService.deactivateAccount(request, accountResponseObserver);

        verify(accountRepository, never()).save(any());
        verify(accountResponseObserver).onNext(argThat(response ->
                response.getStatus().equals("FAILED")
        ));
        verify(accountResponseObserver).onCompleted();
    }

    // ========== linkATMCard Tests ==========

    @Test
    void linkATMCard_Success() {
        when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));

        LinkCardRequest request = LinkCardRequest.newBuilder()
                .setAccountId("ACC-001")
                .setCardNumber("4532015112830366")
                .build();

        accountService.linkATMCard(request, accountResponseObserver);

        verify(cardRepository).save(argThat(card ->
                card.getCardNumber().equals("4532015112830366") &&
                card.getStatus().equals("ACTIVE") &&
                card.getAccount().equals(activeAccount)
        ));
        verify(accountResponseObserver).onNext(argThat(response ->
                response.getStatus().equals("SUCCESS") &&
                response.getMessage().contains("linked successfully")
        ));
        verify(accountResponseObserver).onCompleted();
    }

    @Test
    void linkATMCard_AccountNotFound() {
        when(accountRepository.findById("ACC-999")).thenReturn(Optional.empty());

        LinkCardRequest request = LinkCardRequest.newBuilder()
                .setAccountId("ACC-999")
                .setCardNumber("4532015112830366")
                .build();

        accountService.linkATMCard(request, accountResponseObserver);

        verify(cardRepository, never()).save(any());
        verify(accountResponseObserver).onNext(argThat(response ->
                response.getStatus().equals("FAILED")
        ));
        verify(accountResponseObserver).onCompleted();
    }

    @Test
    void linkATMCard_EmptyCardNumber() {
        when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> {
            Card card = invocation.getArgument(0);
            // Simulate @PrePersist behavior
            if (card.getCardNumber() == null || card.getCardNumber().isEmpty()) {
                card.setCardNumber(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            }
            return card;
        });

        LinkCardRequest request = LinkCardRequest.newBuilder()
                .setAccountId("ACC-001")
                .setCardNumber("")
                .build();

        accountService.linkATMCard(request, accountResponseObserver);

        verify(cardRepository).save(argThat(card ->
                card.getCardNumber() != null && !card.getCardNumber().isEmpty()
        ));
        verify(accountResponseObserver).onCompleted();
    }

    // ========== blockCard Tests ==========

    @Test
    void blockCard_Success() {
        when(cardRepository.findById("4532015112830366")).thenReturn(Optional.of(activeCard));

        CardIdRequest request = CardIdRequest.newBuilder()
                .setCardNumber("4532015112830366")
                .build();

        accountService.blockCard(request, accountResponseObserver);

        verify(cardRepository).save(argThat(card ->
                card.getStatus().equals("BLOCKED")
        ));
        verify(accountResponseObserver).onNext(argThat(response ->
                response.getStatus().equals("SUCCESS") &&
                response.getMessage().contains("blocked")
        ));
        verify(accountResponseObserver).onCompleted();
    }

    @Test
    void blockCard_CardNotFound() {
        when(cardRepository.findById("999999999999")).thenReturn(Optional.empty());

        CardIdRequest request = CardIdRequest.newBuilder()
                .setCardNumber("999999999999")
                .build();

        accountService.blockCard(request, accountResponseObserver);

        verify(cardRepository, never()).save(any());
        verify(accountResponseObserver).onNext(argThat(response ->
                response.getStatus().equals("FAILED") &&
                response.getMessage().contains("not found")
        ));
        verify(accountResponseObserver).onCompleted();
    }

    // ========== getAccountById Tests ==========

    @Test
    void getAccountById_Success() {
        when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));

        AccountIdRequest request = AccountIdRequest.newBuilder()
                .setAccountId("ACC-001")
                .build();

        accountService.getAccountById(request, accountResponseObserver);

        verify(accountResponseObserver).onNext(argThat(response ->
                response.getAccountId().equals("ACC-001") &&
                response.getStatus().equals("SUCCESS") &&
                response.getMessage().equals("Account retrieved")
        ));
        verify(accountResponseObserver).onCompleted();
    }

    @Test
    void getAccountById_NotFound() {
        when(accountRepository.findById("ACC-999")).thenReturn(Optional.empty());

        AccountIdRequest request = AccountIdRequest.newBuilder()
                .setAccountId("ACC-999")
                .build();

        accountService.getAccountById(request, accountResponseObserver);

        verify(accountResponseObserver).onNext(argThat(response ->
                response.getAccountId().equals("ACC-999") &&
                response.getStatus().equals("FAILED")
        ));
        verify(accountResponseObserver).onCompleted();
    }

    // ========== getAccountSummary Tests ==========

    @Test
    void getAccountSummary_Success() {
        when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));

        AccountIdRequest request = AccountIdRequest.newBuilder()
                .setAccountId("ACC-001")
                .build();

        accountService.getAccountSummary(request, summaryResponseObserver);

        verify(summaryResponseObserver).onNext(argThat(response ->
                response.getAccountId().equals("ACC-001") &&
                response.getBalance() == 5000.0 &&
                response.getStatus().equals("ACTIVE")
        ));
        verify(summaryResponseObserver).onCompleted();
    }

    @Test
    void getAccountSummary_NotFound() {
        when(accountRepository.findById("ACC-999")).thenReturn(Optional.empty());

        AccountIdRequest request = AccountIdRequest.newBuilder()
                .setAccountId("ACC-999")
                .build();

        accountService.getAccountSummary(request, summaryResponseObserver);

        verify(summaryResponseObserver).onNext(argThat(response ->
                response.getAccountId().equals("ACC-999") &&
                response.getStatus().equals("NOT_FOUND")
        ));
        verify(summaryResponseObserver).onCompleted();
    }

    @Test
    void getAccountSummary_VerifyBalanceAccuracy() {
        activeAccount.setBalance(12345.67);
        when(accountRepository.findById("ACC-001")).thenReturn(Optional.of(activeAccount));

        AccountIdRequest request = AccountIdRequest.newBuilder()
                .setAccountId("ACC-001")
                .build();

        accountService.getAccountSummary(request, summaryResponseObserver);

        verify(summaryResponseObserver).onNext(argThat(response ->
                response.getBalance() == 12345.67
        ));
        verify(summaryResponseObserver).onCompleted();
    }
}
