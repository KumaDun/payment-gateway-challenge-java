package com.checkout.payment.gateway.bank;

import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;

public interface BankClient {

  String getBankId();

  BankPaymentResponse processPayment(PostPaymentRequest paymentRequest);
}