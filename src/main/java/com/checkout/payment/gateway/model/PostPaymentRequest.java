package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;
import java.time.YearMonth;

public class PostPaymentRequest implements Serializable {

  @JsonProperty("card_number")
  @NotNull(message = "Card number cannot be null")
  @Pattern(
      regexp = "[0-9]{14,19}",
      message = "Card number must contain 14 to 19 digits"
  )
  private String cardNumber;

  @JsonProperty("expiry_month")
  @Min(value = 1, message = "Expiry month must between 1 to 12")
  @Max(value = 12, message = "Expiry month must between 1 to 12")
  private int expiryMonth;

  @JsonProperty("expiry_year")
  @Min(value = 1900, message = "Expiry year cannot be earlier than 1900")
  @Max(value = 9999, message = "Expiry year cannot be later than 9999")
  private int expiryYear;

  @NotNull(message = "Currency symbol cannot be null")
  @Pattern(
      regexp = "USD|EUR|GBP",
      message = "Currency must be one of USD, EUR, GBP"
  )
  private String currency;

  @Positive(message = "Amount must be greater than zero")
  private int amount;

  @NotNull(message = "CVV cannot be null")
  @Pattern(
      regexp = "[0-9]{3,4}",
      message = "CVV must contain exactly 3 or 4 digits"
  )
  private String cvv;

  @JsonIgnore
  @AssertTrue(message = "Card expiry date must be the current month or later")
  public boolean isExpiryDateValid() {
    if (expiryMonth < 1 || expiryMonth > 12
        || expiryYear < 1900 || expiryYear > 9999) {
      return false;
    }

    YearMonth expiry = YearMonth.of(expiryYear, expiryMonth);
    return !expiry.isBefore(YearMonth.now());
  }

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public int getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(int expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public int getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(int expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  @JsonProperty("expiry_date")
  public String getExpiryDate() {
    return String.format("%d/%d", expiryMonth, expiryYear);
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "cardNumberLastFour=" + cardNumber.substring(cardNumber.length() - 4) +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
//        ", cvv=" + cvv +
        '}';
  }
}
