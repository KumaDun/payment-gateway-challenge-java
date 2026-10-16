package com.checkout.payment.gateway.model;

public record BankPaymentResponse(
    boolean authorized,
    String authorizationCode
) {
}