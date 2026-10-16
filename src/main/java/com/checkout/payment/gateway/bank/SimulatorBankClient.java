package com.checkout.payment.gateway.bank;

import com.checkout.payment.gateway.exception.BankBadRequestException;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class SimulatorBankClient implements BankClient {

  private final RestTemplate restTemplate;
  private final String paymentsUrl;

  public SimulatorBankClient(
      RestTemplate restTemplate,
      @Value("${bank.simulator.payments-url}") String paymentsUrl
  ) {
    this.restTemplate = restTemplate;
    this.paymentsUrl = paymentsUrl;
  }

  @Override
  public String getBankId() {
    return "simulator";
  }

  @Override
  public BankPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    String expiryDate = String.format(
        Locale.ROOT,
        "%02d/%04d",
        paymentRequest.getExpiryMonth(),
        paymentRequest.getExpiryYear()
    );

    BankPaymentRequest bankRequest = new BankPaymentRequest(
        paymentRequest.getCardNumber(),
        expiryDate,
        paymentRequest.getCurrency(),
        paymentRequest.getAmount(),
        paymentRequest.getCvv()
    );


    try {
      SimulatorResponse response = restTemplate.postForObject(
          paymentsUrl,
          bankRequest,
          SimulatorResponse.class
      );

      if (response == null || response.authorized() == null) {
        throw new IllegalStateException(
            "Bank simulator returned an invalid response"
        );
      }

      return new BankPaymentResponse(
          response.authorized(),
          response.authorizationCode()
      );
    } catch (HttpClientErrorException.BadRequest e) {
      throw new BankBadRequestException(
          "Bank rejected the request constructed by the gateway",
          e
      );
    } catch (HttpServerErrorException.ServiceUnavailable ex) {
      throw new BankUnavailableException(
          "Bank reported temporary unavailability"
      );
    } catch (ResourceAccessException ex) {
      throw new BankUnavailableException(
          "Bank communication failed; payment outcome may be unknown"
      );
    }
  }

  private record SimulatorResponse(
      Boolean authorized,
      @JsonProperty("authorization_code") String authorizationCode
  ) {
  }
}