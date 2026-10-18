package com.checkout.payment.gateway.exception;

import java.util.UUID;

public class PaymentStorageException extends RuntimeException {

  private final UUID paymentId;

  public PaymentStorageException(String message, UUID paymentId) {
    super(message);
    this.paymentId = paymentId;
  }

  public UUID getPaymentId() {
    return paymentId;
  }
}
