package com.checkout.payment.gateway.bank;

import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class SimulatorBankClient {

  private final RestTemplate restTemplate;
  private final String paymentsUrl;

  public SimulatorBankClient(
      RestTemplate restTemplate,
      @Value("${bank.simulator.payments-url}") String paymentsUrl
  ) {
    this.restTemplate = restTemplate;
    this.paymentsUrl = paymentsUrl;
  }

  public BankPaymentResponse processPayment(BankPaymentRequest bankPaymentRequest) {
    BankPaymentRequest bankRequest = new BankPaymentRequest(
        bankPaymentRequest.getCardNumber(),
        bankPaymentRequest.getExpiryDate(),
        bankPaymentRequest.getCurrency(),
        bankPaymentRequest.getAmount(),
        bankPaymentRequest.getCvv()
    );

    BankPaymentResponse response = restTemplate.postForObject(
        paymentsUrl,
        bankRequest,
        BankPaymentResponse.class
    );

    if (response == null) {
      throw new IllegalStateException(
          "Bank simulator returned an invalid response"
      );
    }

    return new BankPaymentResponse(
        response.authorized(),
        response.authorizationCode()
    );
  }
}