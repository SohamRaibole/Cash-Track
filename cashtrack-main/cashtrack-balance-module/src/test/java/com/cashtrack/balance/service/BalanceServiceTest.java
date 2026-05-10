package com.cashtrack.balance.service;

import com.cashtrack.api.*;
import com.cashtrack.balance.entity.TransactionRecord;
import com.cashtrack.balance.repository.TransactionHistoryRepository;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BalanceServiceTest {

    @Mock
    private TransactionHistoryRepository historyRepository;

    @Mock
    private StreamObserver<BalanceResponse> balanceResponseObserver;

    @Mock
    private StreamObserver<StatementResponse> statementResponseObserver;

    @InjectMocks
    private BalanceServiceGrpcImpl balanceService;

    private List<TransactionRecord> transactionRecords;

    @BeforeEach
    void setUp() {
        TransactionRecord record1 = new TransactionRecord();
        record1.setId("REC-001");
        record1.setAccountId("ACC-001");
        record1.setAmount(500.0);
        record1.setType("DEBIT");
        record1.setTimestamp(LocalDateTime.now().minusHours(1));

        TransactionRecord record2 = new TransactionRecord();
        record2.setId("REC-002");
        record2.setAccountId("ACC-001");
        record2.setAmount(1000.0);
        record2.setType("CREDIT");
        record2.setTimestamp(LocalDateTime.now().minusHours(2));

        TransactionRecord record3 = new TransactionRecord();
        record3.setId("REC-003");
        record3.setAccountId("ACC-001");
        record3.setAmount(200.0);
        record3.setType("DEBIT");
        record3.setTimestamp(LocalDateTime.now().minusHours(3));

        transactionRecords = Arrays.asList(record1, record2, record3);
    }

    // ========== getBalance Tests ==========

    @Test
    void getBalance_Success() {
        BalanceRequest request = BalanceRequest.newBuilder()
                .setAccountId("ACC-001")
                .build();

        balanceService.getBalance(request, balanceResponseObserver);

        verify(balanceResponseObserver).onNext(argThat(response ->
                response.getAccountId().equals("ACC-001") &&
                response.getBalance() == 1500.50
        ));
        verify(balanceResponseObserver).onCompleted();
        verifyNoInteractions(historyRepository);
    }

    @Test
    void getBalance_DifferentAccount() {
        BalanceRequest request = BalanceRequest.newBuilder()
                .setAccountId("ACC-999")
                .build();

        balanceService.getBalance(request, balanceResponseObserver);

        verify(balanceResponseObserver).onNext(argThat(response ->
                response.getAccountId().equals("ACC-999") &&
                response.getBalance() == 1500.50
        ));
        verify(balanceResponseObserver).onCompleted();
    }

    // ========== getMiniStatement Tests ==========

    @Test
    void getMiniStatement_Success() {
        when(historyRepository.findByAccountIdOrderByTimestampDesc("ACC-001"))
                .thenReturn(transactionRecords);

        StatementRequest request = StatementRequest.newBuilder()
                .setAccountId("ACC-001")
                .build();

        balanceService.getMiniStatement(request, statementResponseObserver);

        verify(historyRepository).findByAccountIdOrderByTimestampDesc("ACC-001");
        verify(statementResponseObserver).onNext(argThat(response ->
                response.getAccountId().equals("ACC-001") &&
                response.getStatementDetails().contains("Mini Statement") &&
                response.getStatementDetails().contains("DEBIT") &&
                response.getStatementDetails().contains("CREDIT")
        ));
        verify(statementResponseObserver).onCompleted();
    }

    @Test
    void getMiniStatement_EmptyHistory() {
        when(historyRepository.findByAccountIdOrderByTimestampDesc("ACC-999"))
                .thenReturn(List.of());

        StatementRequest request = StatementRequest.newBuilder()
                .setAccountId("ACC-999")
                .build();

        balanceService.getMiniStatement(request, statementResponseObserver);

        verify(statementResponseObserver).onNext(argThat(response ->
                response.getAccountId().equals("ACC-999")
        ));
        verify(statementResponseObserver).onCompleted();
    }

    @Test
    void getMiniStatement_LimitToFiveTransactions() {
        // Create 10 transactions
        List<TransactionRecord> manyRecords = Arrays.asList(
                createRecord("REC-01", 100.0, "DEBIT"),
                createRecord("REC-02", 200.0, "CREDIT"),
                createRecord("REC-03", 300.0, "DEBIT"),
                createRecord("REC-04", 400.0, "CREDIT"),
                createRecord("REC-05", 500.0, "DEBIT"),
                createRecord("REC-06", 600.0, "CREDIT"),
                createRecord("REC-07", 700.0, "DEBIT"),
                createRecord("REC-08", 800.0, "CREDIT"),
                createRecord("REC-09", 900.0, "DEBIT"),
                createRecord("REC-10", 1000.0, "CREDIT")
        );

        when(historyRepository.findByAccountIdOrderByTimestampDesc("ACC-001"))
                .thenReturn(manyRecords);

        StatementRequest request = StatementRequest.newBuilder()
                .setAccountId("ACC-001")
                .build();

        balanceService.getMiniStatement(request, statementResponseObserver);

        verify(statementResponseObserver).onNext(argThat(response -> {
            // Should only contain first 5 transactions (limited by .limit(5))
            String details = response.getStatementDetails();
            return details.contains("100.0") &&
                    details.contains("200.0") &&
                    details.contains("300.0") &&
                    details.contains("400.0") &&
                    details.contains("500.0");
        }));
        verify(statementResponseObserver).onCompleted();
    }

    @Test
    void getMiniStatement_VerifyRepositoryCalled() {
        when(historyRepository.findByAccountIdOrderByTimestampDesc(anyString()))
                .thenReturn(List.of());

        StatementRequest request = StatementRequest.newBuilder()
                .setAccountId("ACC-001")
                .build();

        balanceService.getMiniStatement(request, statementResponseObserver);

        verify(historyRepository).findByAccountIdOrderByTimestampDesc("ACC-001");
        verify(statementResponseObserver).onCompleted();
    }

    private TransactionRecord createRecord(String id, double amount, String type) {
        TransactionRecord record = new TransactionRecord();
        record.setId(id);
        record.setAccountId("ACC-001");
        record.setAmount(amount);
        record.setType(type);
        record.setTimestamp(LocalDateTime.now());
        return record;
    }
}
