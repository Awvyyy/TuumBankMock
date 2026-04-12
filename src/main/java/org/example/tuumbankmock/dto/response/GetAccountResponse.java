package org.example.tuumbankmock.dto.response;

import java.util.List;

public class GetAccountResponse {
    private Long accountId;
    private Long customerId;
    private List<BalanceResponse> balances;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public List<BalanceResponse> getBalances() {
        return balances;
    }

    public void setBalances(List<BalanceResponse> balances) {
        this.balances = balances;
    }
}
