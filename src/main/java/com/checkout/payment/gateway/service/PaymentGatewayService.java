package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.bank.BankClient;
import com.checkout.payment.gateway.bank.BankRouter;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final BankRouter bankRouter;


  public PaymentGatewayService(
      PaymentsRepository paymentsRepository,
      RestTemplate restTemplate,
      BankRouter bankRouter
  ) {
    this.paymentsRepository = paymentsRepository;
    this.bankRouter = bankRouter;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    BankClient bankClient = bankRouter.selectBank();
    UUID uuid = UUID.randomUUID();
    BankPaymentResponse bankResponse =
        bankClient.processPayment(paymentRequest);

    String cardNumber = paymentRequest.getCardNumber();
    String lastFourDigits =
        cardNumber.substring(cardNumber.length() - 4);

    PostPaymentResponse paymentResponse = new PostPaymentResponse();
    paymentResponse.setId(uuid);
    paymentResponse.setStatus(
        bankResponse.authorized()
            ? PaymentStatus.AUTHORIZED
            : PaymentStatus.DECLINED
    );
    paymentResponse.setCardNumberLastFour(lastFourDigits);
    paymentResponse.setExpiryMonth(paymentRequest.getExpiryMonth());
    paymentResponse.setExpiryYear(paymentRequest.getExpiryYear());
    paymentResponse.setCurrency(paymentRequest.getCurrency());
    paymentResponse.setAmount(paymentRequest.getAmount());

    paymentsRepository.add(paymentResponse);
    return paymentResponse;
  }
}
