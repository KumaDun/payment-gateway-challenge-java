package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.exception.PaymentStorageException;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentsRepository {

  private final ConcurrentHashMap<UUID, PostPaymentResponse> payments = new ConcurrentHashMap<>();

  public void add(PostPaymentResponse payment) {
    if (payments.putIfAbsent(payment.getId(), payment) != null) {
      throw new PaymentStorageException("Payment ID already exists", payment.getId());
    }
  }

  public Optional<PostPaymentResponse> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

}
