package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.checkout.payment.gateway.bank.SimulatorBankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.PaymentStorageException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  private PaymentsRepository repository;
  @Mock
  private SimulatorBankClient bankClient;
  private PaymentGatewayService service;

  @BeforeEach
  void setUp() {
    service = new PaymentGatewayService(repository, bankClient);
  }

  @ParameterizedTest
  @CsvSource({"true, AUTHORIZED", "false, DECLINED"})
  void bankOutcomeIsMappedAndSaved(boolean authorized, PaymentStatus expectedStatus) {
    PostPaymentRequest request = validRequest();
    when(bankClient.processPayment(any())).thenReturn(new BankPaymentResponse(authorized, "code"));

    PostPaymentResponse response = service.processPayment(request);

    assertPaymentDetails(request, response, expectedStatus);
    verify(repository).add(response);
    ArgumentCaptor<BankPaymentRequest> bankRequest = ArgumentCaptor.forClass(BankPaymentRequest.class);
    verify(bankClient).processPayment(bankRequest.capture());
    assertAll(
        () -> assertEquals(request.getCardNumber(), bankRequest.getValue().getCardNumber()),
        () -> assertEquals("12/" + request.getExpiryYear(), bankRequest.getValue().getExpiryDate()),
        () -> assertEquals("USD", bankRequest.getValue().getCurrency()),
        () -> assertEquals(100, bankRequest.getValue().getAmount()),
        () -> assertEquals("123", bankRequest.getValue().getCvv()));
  }

  @ParameterizedTest
  @MethodSource("bankFailures")
  void bankExceptionsBecomeSavedDeclines(RuntimeException failure) {
    PostPaymentRequest request = validRequest();
    when(bankClient.processPayment(any())).thenThrow(failure);

    PostPaymentResponse response = service.processPayment(request);

    assertPaymentDetails(request, response, PaymentStatus.DECLINED);
    verify(bankClient).processPayment(any());
    verify(repository).add(response);
  }

  private static Stream<RuntimeException> bankFailures() {
    return Stream.of(
        new HttpClientErrorException(HttpStatus.BAD_REQUEST),
        new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE),
        new ResourceAccessException("Read timed out"),
        new IllegalStateException("Missing bank response"),
        new RuntimeException("Unexpected bank failure"));
  }

  @Test
  void existingPaymentIsReturnedWithoutContactingBank() {
    UUID id = UUID.randomUUID();
    PostPaymentResponse stored = new PostPaymentResponse();
    stored.setId(id);
    when(repository.get(id)).thenReturn(Optional.of(stored));

    assertSame(stored, service.getPaymentById(id));

    verify(repository).get(id);
    verifyNoInteractions(bankClient);
  }

  @Test
  void missingPaymentThrowsWithoutContactingBank() {
    UUID id = UUID.randomUUID();
    when(repository.get(id)).thenReturn(Optional.empty());

    assertThrows(EventProcessingException.class, () -> service.getPaymentById(id));

    verifyNoInteractions(bankClient);
  }

  @Test
  void separateSubmissionsReceiveDifferentIds() {
    when(bankClient.processPayment(any())).thenReturn(new BankPaymentResponse(true, "code"));

    PostPaymentResponse first = service.processPayment(validRequest());
    PostPaymentResponse second = service.processPayment(validRequest());

    assertNotEquals(first.getId(), second.getId());
    verify(repository).add(first);
    verify(repository).add(second);
  }

  @ParameterizedTest
  @CsvSource({"true, AUTHORIZED", "false, DECLINED"})
  void storageFailuresPropagateUnchangedAndNeverRetryBank(boolean authorized,
      PaymentStatus expectedStatus) {
    when(bankClient.processPayment(any())).thenReturn(new BankPaymentResponse(authorized, "code"));
    AtomicReference<PaymentStorageException> failure = new AtomicReference<>();
    doAnswer(invocation -> {
      PostPaymentResponse payment = invocation.getArgument(0);
      failure.set(new PaymentStorageException("Storage failed", payment.getId()));
      throw failure.get();
    }).when(repository).add(any());

    PaymentStorageException caught = assertThrows(PaymentStorageException.class,
        () -> service.processPayment(validRequest()));

    assertSame(failure.get(), caught);
    ArgumentCaptor<PostPaymentResponse> attemptedSave =
        ArgumentCaptor.forClass(PostPaymentResponse.class);
    verify(repository).add(attemptedSave.capture());
    assertEquals(attemptedSave.getValue().getId(), caught.getPaymentId());
    assertPaymentDetails(validRequest(), attemptedSave.getValue(), expectedStatus);
    verify(bankClient, times(1)).processPayment(any());
    verifyNoMoreInteractions(bankClient, repository);
  }

  private static void assertPaymentDetails(PostPaymentRequest request,
      PostPaymentResponse response, PaymentStatus status) {
    assertAll(
        () -> assertNotNull(response.getId()),
        () -> assertEquals(status, response.getStatus()),
        () -> assertEquals("0001", response.getCardNumberLastFour()),
        () -> assertEquals(request.getExpiryMonth(), response.getExpiryMonth()),
        () -> assertEquals(request.getExpiryYear(), response.getExpiryYear()),
        () -> assertEquals(request.getCurrency(), response.getCurrency()),
        () -> assertEquals(request.getAmount(), response.getAmount()));
  }

  private static PostPaymentRequest validRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("4111111111110001");
    request.setExpiryMonth(12);
    request.setExpiryYear(YearMonth.now().plusYears(1).getYear());
    request.setCurrency("USD");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }
}
