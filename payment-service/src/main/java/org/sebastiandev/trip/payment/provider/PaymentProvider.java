package org.sebastiandev.trip.payment.provider;
public interface PaymentProvider { Result charge(String methodRef,long amountMinor,String currency); Result refund(String transactionId,String methodRef); record Result(boolean successful,String transactionId,String reason){} }
