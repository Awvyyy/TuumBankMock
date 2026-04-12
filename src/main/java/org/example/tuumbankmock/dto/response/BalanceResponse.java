package org.example.tuumbankmock.dto.response;

import org.example.tuumbankmock.model.Currency;
import java.math.BigDecimal;

public class BalanceResponse {
    private Currency currency;
    private BigDecimal availableAmount;

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public BigDecimal getAvailableAmount() {
        return availableAmount;
    }

    public void setAvailableAmount(BigDecimal availableAmount) {
        this.availableAmount = availableAmount;
    }
}
