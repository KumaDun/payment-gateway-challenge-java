package com.checkout.payment.gateway.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.PaymentStorageException;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentsRepositoryTest {

  private PaymentsRepository repository;

  @BeforeEach
  void setUp() {
    repository = new PaymentsRepository();
  }

  @Test
  void storedPaymentCanBeRetrievedWithItsDetails() {
    PostPaymentResponse payment = payment(UUID.randomUUID(), PaymentStatus.AUTHORIZED);

    repository.add(payment);

    PostPaymentResponse retrieved = repository.get(payment.getId()).orElseThrow();
    assertAll(
        () -> assertEquals(payment.getId(), retrieved.getId()),
        () -> assertEquals(PaymentStatus.AUTHORIZED, retrieved.getStatus()),
        () -> assertEquals("0001", retrieved.getCardNumberLastFour()),
        () -> assertEquals(12, retrieved.getExpiryMonth()),
        () -> assertEquals(2030, retrieved.getExpiryYear()),
        () -> assertEquals("USD", retrieved.getCurrency()),
        () -> assertEquals(100, retrieved.getAmount()));
  }

  @Test
  void unknownIdReturnsEmpty() {
    assertTrue(repository.get(UUID.randomUUID()).isEmpty());
  }

  @Test
  void duplicateIdIsRejectedWithoutReplacingOriginalPayment() {
    UUID id = UUID.randomUUID();
    PostPaymentResponse original = payment(id, PaymentStatus.AUTHORIZED);
    PostPaymentResponse duplicate = payment(id, PaymentStatus.DECLINED);
    repository.add(original);

    PaymentStorageException failure = assertThrows(PaymentStorageException.class,
        () -> repository.add(duplicate));

    assertEquals(id, failure.getPaymentId());
    assertSame(original, repository.get(id).orElseThrow());
    assertEquals(PaymentStatus.AUTHORIZED, repository.get(id).orElseThrow().getStatus());
  }

  @Test
  void paymentsWithDifferentIdsAreStoredIndependently() {
    PostPaymentResponse first = payment(UUID.randomUUID(), PaymentStatus.AUTHORIZED);
    PostPaymentResponse second = payment(UUID.randomUUID(), PaymentStatus.DECLINED);

    repository.add(first);
    repository.add(second);

    assertEquals(PaymentStatus.AUTHORIZED,
        repository.get(first.getId()).orElseThrow().getStatus());
    assertEquals(PaymentStatus.DECLINED,
        repository.get(second.getId()).orElseThrow().getStatus());
  }

  private static PostPaymentResponse payment(UUID id, PaymentStatus status) {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(id);
    payment.setStatus(status);
    payment.setCardNumberLastFour("0001");
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2030);
    payment.setCurrency("USD");
    payment.setAmount(100);
    return payment;
  }
}
