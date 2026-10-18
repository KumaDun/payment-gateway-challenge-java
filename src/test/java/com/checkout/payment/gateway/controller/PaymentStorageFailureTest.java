package com.checkout.payment.gateway.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.checkout.payment.gateway.bank.SimulatorBankClient;
import com.checkout.payment.gateway.exception.CommonExceptionHandler;
import com.checkout.payment.gateway.exception.PaymentStorageException;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.time.YearMonth;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PaymentStorageFailureTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void storageFailureReturnsSafe500WithoutRetryingAuthorization(boolean authorized) throws Exception {
    PaymentsRepository repository = mock(PaymentsRepository.class);
    SimulatorBankClient bank = mock(SimulatorBankClient.class);
    when(bank.processPayment(any())).thenReturn(new BankPaymentResponse(authorized, "code"));
    doAnswer(invocation -> {
      PostPaymentResponse payment = invocation.getArgument(0);
      throw new PaymentStorageException("private-storage-diagnostic", payment.getId());
    }).when(repository).add(any());

    MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new PaymentGatewayController(new PaymentGatewayService(repository, bank)))
        .setControllerAdvice(new CommonExceptionHandler())
        .build();

    mvc.perform(post("/api/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"card_number":"4111111111110001","expiry_month":12,"expiry_year":%d,
                 "currency":"USD","amount":100,"cvv":"123"}
                """.formatted(YearMonth.now().plusYears(1).getYear())))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value(
            "Unable to store payment result. The payment may have been authorized; "
                + "do not automatically resubmit. Contact support with the payment ID."))
        .andExpect(jsonPath("$.status").doesNotExist())
        .andExpect(jsonPath("$.cause").doesNotExist())
        .andExpect(jsonPath("$.stackTrace").doesNotExist())
        .andExpect(content().string(not(containsString("private-storage-diagnostic"))))
        .andExpect(jsonPath("$.cardNumber").doesNotExist())
        .andExpect(jsonPath("$.cvv").doesNotExist());

    ArgumentCaptor<PostPaymentResponse> attemptedSave =
        ArgumentCaptor.forClass(PostPaymentResponse.class);
    verify(repository).add(attemptedSave.capture());
    assertEquals(authorized ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED,
        attemptedSave.getValue().getStatus());
    verify(bank, times(1)).processPayment(any());
    verifyNoMoreInteractions(bank, repository);
  }
}
