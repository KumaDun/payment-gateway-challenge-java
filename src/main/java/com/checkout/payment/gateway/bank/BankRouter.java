package com.checkout.payment.gateway.bank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BankRouter {
  private final Map<String, BankClient> bankClients;
  private final String defaultBankId;

  public BankRouter(
      List<BankClient> bankClientList,
      @Value("${bank.default-id}") String defaultBankId
  ) {
    this.bankClients = new HashMap<>();
    bankClientList.forEach(bankClient -> bankClients.put(bankClient.getBankId(), bankClient));
    this.defaultBankId = defaultBankId;
    if (!this.bankClients.containsKey(defaultBankId)) {
      throw new IllegalArgumentException("Default bank id is not configured in clients map" + defaultBankId);
    }
  }

  public BankClient selectBank() {
    return bankClients.get(defaultBankId);
  }
}
