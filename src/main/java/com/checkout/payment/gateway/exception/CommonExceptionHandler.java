package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("Exception happened", ex);
    return new ResponseEntity<>(new ErrorResponse("Page not found"),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(PaymentNotFoundException.class)
  public ResponseEntity<ErrorResponse> handlePaymentNotFound(
      PaymentNotFoundException ex) {
    LOG.error("Exception happened", ex);
    return new ResponseEntity<>(new ErrorResponse("Payment not found"),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(BankUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleBankUnavailable(
      BankUnavailableException ex) {
    LOG.error("Bank communication failed");

    return new ResponseEntity<>(new ErrorResponse("Bank communication error"),
        HttpStatus.SERVICE_UNAVAILABLE);
  }

  @ExceptionHandler(BankBadRequestException.class)
  public ResponseEntity<ErrorResponse> handleBankUnavailable(
      BankBadRequestException ex) {
    LOG.error("Bank rejected the request constructed by the gateway");

    return new ResponseEntity<>(new ErrorResponse("Bad bank request"),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }

}
