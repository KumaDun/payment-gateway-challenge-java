package com.checkout.payment.gateway.controller;


import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import java.time.YearMonth;
import java.net.SocketTimeoutException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentsRepository paymentsRepository;

  @Autowired
  private RestTemplate restTemplate;
  @Autowired
  private ObjectMapper objectMapper;
  private MockRestServiceServer bank;

  @BeforeEach
  void setUpBank() {
    bank = MockRestServiceServer.bindTo(restTemplate).build();
  }

  @AfterEach
  void verifyBankCalls() {
    bank.verify();
  }

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour("4321");

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/api/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/api/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }

  @ParameterizedTest
  @CsvSource({"true, Authorized", "false, Declined"})
  void submittedPaymentCanBeRetrievedWithMaskedCard(boolean authorized, String expectedStatus)
      throws Exception {
    ObjectNode request = validRequest();
    YearMonth expiry = YearMonth.now().plusYears(1).withMonth(12);
    bank.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""
            {"card_number":"4111111111110001","expiry_date":"%02d/%04d",
             "currency":"USD","amount":100,"cvv":"123"}
            """.formatted(expiry.getMonthValue(), expiry.getYear()), true))
        .andRespond(withSuccess("{\"authorized\":" + authorized
            + ",\"authorization_code\":\"test-code\"}", MediaType.APPLICATION_JSON));

    String response = mvc.perform(MockMvcRequestBuilders.post("/api/payment")
            .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value(expectedStatus))
        .andExpect(jsonPath("$.cardNumberLastFour").value("0001"))
        .andExpect(jsonPath("$.card_number").doesNotExist())
        .andExpect(jsonPath("$.cardNumber").doesNotExist())
        .andExpect(jsonPath("$.cvv").doesNotExist())
        .andReturn().getResponse().getContentAsString();

    UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    String retrieved = mvc.perform(MockMvcRequestBuilders.get("/api/payment/" + id))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertEquals(objectMapper.readTree(response), objectMapper.readTree(retrieved));
  }

  @ParameterizedTest
  @CsvSource({"card_number,123", "card_number,411111111111111x",
      "card_number,12345678901234567890", "expiry_month,0", "expiry_month,13",
      "expiry_year,1899", "expiry_year,10000", "currency,JPY", "currency,usd",
      "amount,0", "amount,-1", "cvv,12", "cvv,12x", "cvv,12345"})
  void invalidFieldsAreRejectedBeforeCallingBank(String field, String value) throws Exception {
    ObjectNode request = validRequest();
    if (field.equals("expiry_month") || field.equals("expiry_year") || field.equals("amount")) {
      request.put(field, Integer.parseInt(value));
    } else {
      request.put(field, value);
    }
    assertRejected(request);
  }

  @ParameterizedTest
  @ValueSource(strings = {"card_number", "expiry_month", "expiry_year", "currency", "amount", "cvv"})
  void missingRequiredFieldsAreRejected(String field) throws Exception {
    ObjectNode request = validRequest();
    request.remove(field);
    assertRejected(request);
  }

  @Test
  void expiredCardIsRejected() throws Exception {
    YearMonth expired = YearMonth.now().minusMonths(1);
    ObjectNode request = validRequest();
    request.put("expiry_month", expired.getMonthValue());
    request.put("expiry_year", expired.getYear());
    assertRejected(request);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 500, 503})
  void bankHttpErrorsReturnRetrievableDeclinedPayment(int bankStatus) throws Exception {
    bank.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withStatus(HttpStatus.valueOf(bankStatus))
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"internal_error\":\"private-bank-diagnostic\"}"));
    assertDeclinedAndRetrievable();
  }

  @Test
  void bankTimeoutReturnsRetrievableDeclinedPayment() throws Exception {
    bank.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withException(new SocketTimeoutException("private-bank-diagnostic")));
    assertDeclinedAndRetrievable();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "{", "{}", "{\"authorized\":null}"})
  void emptyMalformedOrMissingAuthorizationReturnsDeclined(String bankBody) throws Exception {
    bank.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withSuccess(bankBody, MediaType.APPLICATION_JSON));
    assertDeclinedAndRetrievable();
  }

  @ParameterizedTest
  @ValueSource(strings = {"card_number", "expiry_month", "expiry_year", "currency", "amount", "cvv"})
  void nullRequiredFieldsAreRejectedBeforeCallingBank(String field) throws Exception {
    ObjectNode request = validRequest();
    request.putNull(field);
    assertRejected(request);
  }

  @Test
  void malformedMerchantJsonIsRejectedBeforeCallingBank() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/api/payment")
            .contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void invalidPaymentIdReturns400() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/api/payment/not-a-uuid"))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @CsvSource({"USD,123", "EUR,1234", "GBP,123"})
  void supportedCurrenciesAndCvvLengthsAreAccepted(String currency, String cvv) throws Exception {
    ObjectNode request = validRequest().put("currency", currency).put("cvv", cvv);
    bank.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(content().json("{\"currency\":\"" + currency + "\",\"cvv\":\"" + cvv + "\"}"))
        .andRespond(withSuccess("{\"authorized\":true}", MediaType.APPLICATION_JSON));
    mvc.perform(MockMvcRequestBuilders.post("/api/payment")
            .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"));
  }

  @Test
  void cardExpiringThisMonthIsAccepted() throws Exception {
    YearMonth current = YearMonth.now();
    ObjectNode request = validRequest()
        .put("expiry_month", current.getMonthValue()).put("expiry_year", current.getYear());
    bank.expect(requestTo("http://localhost:8080/payments"))
        .andRespond(withSuccess("{\"authorized\":true}", MediaType.APPLICATION_JSON));
    mvc.perform(MockMvcRequestBuilders.post("/api/payment")
            .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"));
  }

  private void assertDeclinedAndRetrievable() throws Exception {
    String response = mvc.perform(MockMvcRequestBuilders.post("/api/payment")
            .contentType(MediaType.APPLICATION_JSON).content(validRequest().toString()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("0001"))
        .andExpect(jsonPath("$.card_number").doesNotExist())
        .andExpect(jsonPath("$.cardNumber").doesNotExist())
        .andExpect(jsonPath("$.cvv").doesNotExist())
        .andExpect(jsonPath("$.message").doesNotExist())
        .andExpect(result -> assertFalse(
            result.getResponse().getContentAsString().contains("private-bank-diagnostic")))
        .andReturn().getResponse().getContentAsString();
    UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    String retrieved = mvc.perform(MockMvcRequestBuilders.get("/api/payment/" + id))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertEquals(objectMapper.readTree(response), objectMapper.readTree(retrieved));
  }

  private ObjectNode validRequest() {
    YearMonth expiry = YearMonth.now().plusYears(1).withMonth(12);
    return objectMapper.createObjectNode()
        .put("card_number", "4111111111110001")
        .put("expiry_month", expiry.getMonthValue())
        .put("expiry_year", expiry.getYear())
        .put("currency", "USD").put("amount", 100).put("cvv", "123");
  }

  private void assertRejected(ObjectNode request) throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/api/payment")
            .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
        .andExpect(status().isBadRequest());
  }
}
