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

  @ExceptionHandler(PaymentStorageException.class)
  public ResponseEntity<ErrorResponse> handleStorageException(
      PaymentStorageException ex) {
    LOG.error(
        "Unable to store payment result paymentId={} bank authorization may already have occurred",
        ex.getPaymentId(),
        ex
    );
    return new ResponseEntity<>(
        new ErrorResponse("Unable to store payment result. The payment may have been authorized; "
        + "do not automatically resubmit. Contact support with the payment ID."),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("EventProcessingException happened", ex);
    return new ResponseEntity<>(new ErrorResponse("Page not found"),
        HttpStatus.NOT_FOUND);
  }
}
