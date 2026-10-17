package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BankPaymentResponse(
    boolean authorized,
    @JsonProperty("authorization_code") String authorizationCode
) {
}