package com.checkout.payment.gateway.exception;

public class BankBadRequestException extends  RuntimeException{
  public BankBadRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
