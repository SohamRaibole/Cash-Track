package com.cashtrack.account.service;

import com.cashtrack.api.*;
import com.cashtrack.account.entity.Account;
import com.cashtrack.account.entity.Card;
import com.cashtrack.account.repository.AccountRepository;
import com.cashtrack.account.repository.CardRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@GrpcService
public class AccountServiceGrpcImpl extends AccountServiceGrpc.AccountServiceImplBase {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CardRepository cardRepository;

    @GrpcClient("notificationService")
    private NotificationServiceGrpc.NotificationServiceBlockingStub notificationServiceBlockingStub;

    @Override
    @Transactional
    public void createAccount(CreateAccountRequest request, StreamObserver<AccountResponse> responseObserver) {
        Account account = new Account();
        account.setCustomerName(request.getCustomerName());
        account.setKycDetails(request.getKycDetails());
        account.setBalance(request.getInitialDeposit());
        account.setStatus("ACTIVE");

        accountRepository.save(account);

        try {
            notificationServiceBlockingStub.withDeadlineAfter(2, TimeUnit.SECONDS).sendTransactionAlert(TransactionAlertRequest.newBuilder()
                    .setAccountId(account.getAccountId())
                    .setTransactionDetails("Account created with opening balance " + account.getBalance())
                    .build());
        } catch (Exception ignored) {
            // Notification errors should not fail account creation.
        }

        AccountResponse response = AccountResponse.newBuilder()
                .setAccountId(account.getAccountId())
                .setStatus("SUCCESS")
                .setMessage("Account created successfully")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void updateAccountDetails(UpdateAccountRequest request, StreamObserver<AccountResponse> responseObserver) {
        Optional<Account> accountOpt = accountRepository.findById(request.getAccountId());
        
        if (accountOpt.isPresent()) {
            Account account = accountOpt.get();
            account.setCustomerName(request.getCustomerName());
            account.setKycDetails(request.getKycDetails());
            accountRepository.save(account);

            responseObserver.onNext(AccountResponse.newBuilder()
                    .setAccountId(account.getAccountId())
                    .setStatus("SUCCESS")
                    .setMessage("Account updated successfully")
                    .build());
        } else {
            responseObserver.onNext(AccountResponse.newBuilder()
                    .setAccountId(request.getAccountId())
                    .setStatus("FAILED")
                    .setMessage("Account not found")
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void deactivateAccount(AccountIdRequest request, StreamObserver<AccountResponse> responseObserver) {
        Optional<Account> accountOpt = accountRepository.findById(request.getAccountId());
        
        if (accountOpt.isPresent()) {
            Account account = accountOpt.get();
            account.setStatus("INACTIVE");
            accountRepository.save(account);

            responseObserver.onNext(AccountResponse.newBuilder()
                    .setAccountId(account.getAccountId())
                    .setStatus("SUCCESS")
                    .setMessage("Account deactivated successfully")
                    .build());
        } else {
            responseObserver.onNext(AccountResponse.newBuilder()
                    .setAccountId(request.getAccountId())
                    .setStatus("FAILED")
                    .setMessage("Account not found")
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void linkATMCard(LinkCardRequest request, StreamObserver<AccountResponse> responseObserver) {
        Optional<Account> accountOpt = accountRepository.findById(request.getAccountId());
        
        if (accountOpt.isPresent()) {
            Card card = new Card();
            card.setAccount(accountOpt.get());
            if (!request.getCardNumber().isEmpty()) {
                card.setCardNumber(request.getCardNumber());
            }
            card.setStatus("ACTIVE");
            card.setPinHash("DEFAULT_HASH"); // In real app, this should be set properly
            
            cardRepository.save(card);

            responseObserver.onNext(AccountResponse.newBuilder()
                    .setAccountId(accountOpt.get().getAccountId())
                    .setStatus("SUCCESS")
                    .setMessage("Card linked successfully: " + card.getCardNumber())
                    .build());
        } else {
            responseObserver.onNext(AccountResponse.newBuilder()
                    .setAccountId(request.getAccountId())
                    .setStatus("FAILED")
                    .setMessage("Account not found")
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void blockCard(CardIdRequest request, StreamObserver<AccountResponse> responseObserver) {
        Optional<Card> cardOpt = cardRepository.findById(request.getCardNumber());
        
        if (cardOpt.isPresent()) {
            Card card = cardOpt.get();
            card.setStatus("BLOCKED");
            cardRepository.save(card);

            responseObserver.onNext(AccountResponse.newBuilder()
                    .setAccountId(card.getAccount().getAccountId())
                    .setStatus("SUCCESS")
                    .setMessage("Card blocked successfully")
                    .build());
        } else {
            responseObserver.onNext(AccountResponse.newBuilder()
                    .setAccountId("")
                    .setStatus("FAILED")
                    .setMessage("Card not found")
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void getAccountById(AccountIdRequest request, StreamObserver<AccountResponse> responseObserver) {
        Optional<Account> accountOpt = accountRepository.findById(request.getAccountId());
        
        if (accountOpt.isPresent()) {
            responseObserver.onNext(AccountResponse.newBuilder()
                    .setAccountId(accountOpt.get().getAccountId())
                    .setStatus("SUCCESS")
                    .setMessage("Account retrieved")
                    .build());
        } else {
            responseObserver.onNext(AccountResponse.newBuilder()
                    .setAccountId(request.getAccountId())
                    .setStatus("FAILED")
                    .setMessage("Account not found")
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void getAccountSummary(AccountIdRequest request, StreamObserver<AccountSummaryResponse> responseObserver) {
        Optional<Account> accountOpt = accountRepository.findById(request.getAccountId());
        
        if (accountOpt.isPresent()) {
            Account account = accountOpt.get();
            responseObserver.onNext(AccountSummaryResponse.newBuilder()
                    .setAccountId(account.getAccountId())
                    .setBalance(account.getBalance())
                    .setStatus(account.getStatus())
                    .build());
        } else {
            responseObserver.onNext(AccountSummaryResponse.newBuilder()
                    .setAccountId(request.getAccountId())
                    .setStatus("NOT_FOUND")
                    .build());
        }
        responseObserver.onCompleted();
    }
}
