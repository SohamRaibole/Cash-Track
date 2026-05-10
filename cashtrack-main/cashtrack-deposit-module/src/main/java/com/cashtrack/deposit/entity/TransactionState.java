package com.cashtrack.deposit.entity;

public sealed interface TransactionState permits 
    TransactionState.Initiated, 
    TransactionState.Authorized, 
    TransactionState.Processing, 
    TransactionState.Completed, 
    TransactionState.Failed, 
    TransactionState.Reversed {

    record Initiated() implements TransactionState {}
    record Authorized() implements TransactionState {}
    record Processing() implements TransactionState {}
    record Completed() implements TransactionState {}
    record Failed() implements TransactionState {}
    record Reversed() implements TransactionState {}
}
