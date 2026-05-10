package com.cashtrack.machine.service;

import com.cashtrack.api.*;
import com.cashtrack.machine.entity.ATMMachine;
import com.cashtrack.machine.repository.ATMRepository;
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
public class MachineServiceTest {

    @Mock
    private ATMRepository atmRepository;

    @Mock
    private StreamObserver<ATMResponse> atmResponseObserver;

    @Mock
    private StreamObserver<ATMCashResponse> cashResponseObserver;

    @InjectMocks
    private MachineServiceGrpcImpl machineService;

    private ATMMachine activeATM;
    private ATMMachine outOfCashATM;

    @BeforeEach
    void setUp() {
        activeATM = new ATMMachine();
        activeATM.setId("ATM-001");
        activeATM.setLocation("123 Main St, New York, NY");
        activeATM.setCashBalance(50000.0);
        activeATM.setStatus("ACTIVE");

        outOfCashATM = new ATMMachine();
        outOfCashATM.setId("ATM-002");
        outOfCashATM.setLocation("456 Broadway, New York, NY");
        outOfCashATM.setCashBalance(0.0);
        outOfCashATM.setStatus("OUT_OF_CASH");
    }

    // ========== registerATM Tests ==========

    @Test
    void registerATM_Success() {
        RegisterATMRequest request = RegisterATMRequest.newBuilder()
                .setLocation("123 Main St, New York, NY")
                .build();

        when(atmRepository.save(any(ATMMachine.class))).thenAnswer(invocation -> {
            ATMMachine atm = invocation.getArgument(0);
            atm.setId("ATM-NEW");
            return atm;
        });

        machineService.registerATM(request, atmResponseObserver);

        ArgumentCaptor<ATMMachine> atmCaptor = ArgumentCaptor.forClass(ATMMachine.class);
        verify(atmRepository).save(atmCaptor.capture());
        ATMMachine savedATM = atmCaptor.getValue();

        assertEquals("123 Main St, New York, NY", savedATM.getLocation());
        assertEquals(0.0, savedATM.getCashBalance());
        assertEquals("ACTIVE", savedATM.getStatus());

        verify(atmResponseObserver).onNext(argThat(response ->
                response.getAtmId().equals("ATM-NEW") &&
                response.getStatus().equals("ACTIVE")
        ));
        verify(atmResponseObserver).onCompleted();
    }

    @Test
    void registerATM_VerifyInitialState() {
        RegisterATMRequest request = RegisterATMRequest.newBuilder()
                .setLocation("456 Broadway, New York, NY")
                .build();

        when(atmRepository.save(any(ATMMachine.class))).thenAnswer(invocation -> {
            ATMMachine atm = invocation.getArgument(0);
            // Simulate @GeneratedValue behavior
            if (atm.getId() == null) {
                atm.setId(java.util.UUID.randomUUID().toString());
            }
            return atm;
        });

        machineService.registerATM(request, atmResponseObserver);

        verify(atmRepository).save(argThat(atm ->
                atm.getCashBalance() == 0.0 &&
                atm.getStatus().equals("ACTIVE") &&
                atm.getLocation().equals("456 Broadway, New York, NY")
        ));
        verify(atmResponseObserver).onNext(argThat(response ->
                response.getAtmId() != null &&
                response.getStatus().equals("ACTIVE")
        ));
        verify(atmResponseObserver).onCompleted();
    }

    @Test
    void registerATM_EmptyLocation() {
        RegisterATMRequest request = RegisterATMRequest.newBuilder()
                .setLocation("")
                .build();

        when(atmRepository.save(any(ATMMachine.class))).thenAnswer(invocation -> {
            ATMMachine atm = invocation.getArgument(0);
            atm.setId("ATM-EMPTY");
            return atm;
        });

        machineService.registerATM(request, atmResponseObserver);

        verify(atmRepository).save(argThat(atm -> atm.getLocation().equals("")));
        verify(atmResponseObserver).onCompleted();
    }

    // ========== getATMCashStatus Tests ==========

    @Test
    void getATMCashStatus_Success() {
        when(atmRepository.findById("ATM-001")).thenReturn(Optional.of(activeATM));

        ATMIdRequest request = ATMIdRequest.newBuilder()
                .setAtmId("ATM-001")
                .build();

        machineService.getATMCashStatus(request, cashResponseObserver);

        verify(cashResponseObserver).onNext(argThat(response ->
                response.getAtmId().equals("ATM-001") &&
                response.getCashAvailable() == 50000.0
        ));
        verify(cashResponseObserver).onCompleted();
    }

    @Test
    void getATMCashStatus_OutOfCash() {
        when(atmRepository.findById("ATM-002")).thenReturn(Optional.of(outOfCashATM));

        ATMIdRequest request = ATMIdRequest.newBuilder()
                .setAtmId("ATM-002")
                .build();

        machineService.getATMCashStatus(request, cashResponseObserver);

        verify(cashResponseObserver).onNext(argThat(response ->
                response.getAtmId().equals("ATM-002") &&
                response.getCashAvailable() == 0.0
        ));
        verify(cashResponseObserver).onCompleted();
    }

    @Test
    void getATMCashStatus_NotFound() {
        when(atmRepository.findById("ATM-999")).thenReturn(Optional.empty());

        ATMIdRequest request = ATMIdRequest.newBuilder()
                .setAtmId("ATM-999")
                .build();

        machineService.getATMCashStatus(request, cashResponseObserver);

        verify(cashResponseObserver).onNext(argThat(response ->
                response.getAtmId().equals("ATM-999") &&
                response.getCashAvailable() == -1
        ));
        verify(cashResponseObserver).onCompleted();
    }

    @Test
    void getATMCashStatus_VerifyBalanceAccuracy() {
        activeATM.setCashBalance(123456.78);
        when(atmRepository.findById("ATM-001")).thenReturn(Optional.of(activeATM));

        ATMIdRequest request = ATMIdRequest.newBuilder()
                .setAtmId("ATM-001")
                .build();

        machineService.getATMCashStatus(request, cashResponseObserver);

        verify(cashResponseObserver).onNext(argThat(response ->
                response.getCashAvailable() == 123456.78
        ));
        verify(cashResponseObserver).onCompleted();
    }

    // ========== Integration Tests ==========

    @Test
    void registerAndGetCashStatus() {
        // Register ATM
        RegisterATMRequest registerReq = RegisterATMRequest.newBuilder()
                .setLocation("789 5th Ave, New York, NY")
                .build();

        when(atmRepository.save(any(ATMMachine.class))).thenAnswer(invocation -> {
            ATMMachine atm = invocation.getArgument(0);
            atm.setId("ATM-INT");
            return atm;
        });

        machineService.registerATM(registerReq, atmResponseObserver);
        verify(atmResponseObserver).onNext(argThat(r -> r.getAtmId().equals("ATM-INT")));

        // Get cash status
        ATMMachine newATM = new ATMMachine();
        newATM.setId("ATM-INT");
        newATM.setLocation("789 5th Ave, New York, NY");
        newATM.setCashBalance(0.0);
        newATM.setStatus("ACTIVE");

        when(atmRepository.findById("ATM-INT")).thenReturn(Optional.of(newATM));

        ATMIdRequest cashReq = ATMIdRequest.newBuilder()
                .setAtmId("ATM-INT")
                .build();

        machineService.getATMCashStatus(cashReq, cashResponseObserver);

        verify(cashResponseObserver).onNext(argThat(response ->
                response.getAtmId().equals("ATM-INT") &&
                response.getCashAvailable() == 0.0
        ));
        verify(cashResponseObserver).onCompleted();
    }
}
