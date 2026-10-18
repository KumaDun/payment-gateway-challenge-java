package com.checkout.payment.gateway.bank;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

class SimulatorBankClientTest {

  private static final String PAYMENTS_URL = "http://bank.test/payments";
  private SimulatorBankClient client;
  private MockRestServiceServer bank;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    bank = MockRestServiceServer.bindTo(restTemplate).build();
    client = new SimulatorBankClient(restTemplate, PAYMENTS_URL);
  }

  @AfterEach
  void verifyBankCalls() {
    bank.verify();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void sendsBankJsonAndMapsAuthorizationCode(boolean authorized) {
    String code = authorized ? "bank-authorization-123" : "";
    bank.expect(requestTo(PAYMENTS_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json("""
            {"card_number":"4111111111110001","expiry_date":"01/2030",
             "currency":"USD","amount":100,"cvv":"123"}
            """, true))
        .andRespond(withSuccess("{\"authorized\":" + authorized
            + ",\"authorization_code\":\"" + code + "\"}", MediaType.APPLICATION_JSON));

    BankPaymentResponse response = client.processPayment(request());

    assertEquals(authorized, response.authorized());
    assertEquals(code, response.authorizationCode());
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 429, 500, 502, 503})
  void bankHttpErrorsPropagateToCaller(int status) {
    bank.expect(requestTo(PAYMENTS_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

    HttpStatusCodeException exception = assertThrows(HttpStatusCodeException.class,
        () -> client.processPayment(request()));

    assertEquals(status, exception.getStatusCode().value());
  }

  @Test
  void communicationFailurePropagatesWithItsCause() {
    SocketTimeoutException failure = new SocketTimeoutException("Read timed out");
    bank.expect(requestTo(PAYMENTS_URL)).andRespond(withException(failure));

    ResourceAccessException exception = assertThrows(ResourceAccessException.class,
        () -> client.processPayment(request()));

    assertSame(failure, exception.getCause());
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 204})
  void emptyBankResponseIsRejected(int status) {
    bank.expect(requestTo(PAYMENTS_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

    assertThrows(IllegalStateException.class, () -> client.processPayment(request()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{", "{\"authorized\":{}}"})
  void malformedBankResponsePropagatesAsClientFailure(String body) {
    bank.expect(requestTo(PAYMENTS_URL))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThrows(RestClientException.class, () -> client.processPayment(request()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"authorized\":null}"})
  void primitiveAuthorizationDefaultsToFalseWhenMissingOrNull(String body) {
    bank.expect(requestTo(PAYMENTS_URL))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertFalse(client.processPayment(request()).authorized());
  }

  private static BankPaymentRequest request() {
    return new BankPaymentRequest("4111111111110001", "01/2030", "USD", 100, "123");
  }
}
