package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.bank.SimulatorBankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);
  private final PaymentsRepository paymentsRepository;
  private final SimulatorBankClient bankClient;


  public PaymentGatewayService(
      PaymentsRepository paymentsRepository,
      SimulatorBankClient bankClient
  ) {
    this.paymentsRepository = paymentsRepository;
    this.bankClient = bankClient;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    UUID uuid = UUID.randomUUID();
    PaymentStatus status = PaymentStatus.DECLINED;
    BankPaymentRequest bankPaymentRequest = new BankPaymentRequest(
        paymentRequest.getCardNumber(),
        paymentRequest.getExpiryDate(),
        paymentRequest.getCurrency(),
        paymentRequest.getAmount(),
        paymentRequest.getCvv()
    );
    try {
      BankPaymentResponse bankResponse = bankClient.processPayment(bankPaymentRequest);
      if (bankResponse != null & bankResponse.authorized()) {
        status = PaymentStatus.AUTHORIZED;
      }
    } catch (Exception e) {
      LOG.error("Error processing payment", e);
    }

    PostPaymentResponse paymentResponse = buildPostPaymentResponse(paymentRequest, uuid, status);
    paymentsRepository.add(paymentResponse);
    return paymentResponse;
  }

  @Nonnull
  private static PostPaymentResponse buildPostPaymentResponse(PostPaymentRequest paymentRequest,
      UUID uuid, PaymentStatus status) {
    String cardNumber = paymentRequest.getCardNumber();
    String lastFourDigits =
        cardNumber.substring(cardNumber.length() - 4);

    PostPaymentResponse paymentResponse = new PostPaymentResponse();
    paymentResponse.setId(uuid);
    paymentResponse.setStatus(status);
    paymentResponse.setCardNumberLastFour(lastFourDigits);
    paymentResponse.setExpiryMonth(paymentRequest.getExpiryMonth());
    paymentResponse.setExpiryYear(paymentRequest.getExpiryYear());
    paymentResponse.setCurrency(paymentRequest.getCurrency());
    paymentResponse.setAmount(paymentRequest.getAmount());
    return paymentResponse;
  }
}
