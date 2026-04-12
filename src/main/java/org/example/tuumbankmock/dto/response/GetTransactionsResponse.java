package org.example.tuumbankmock.dto.response;

import java.util.List;

public class GetTransactionsResponse {
    private Long accountId;
    private List<TransactionResponse> transactions;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public List<TransactionResponse> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<TransactionResponse> transactions) {
        this.transactions = transactions;
    }
}
