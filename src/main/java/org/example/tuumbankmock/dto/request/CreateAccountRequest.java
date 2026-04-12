package org.example.tuumbankmock.dto.request;

import org.example.tuumbankmock.model.Currency;
import java.util.List;

public class CreateAccountRequest {
    private Long customerId;
    private String country;
    private List<Currency> currencies;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public List<Currency> getCurrencies() {
        return currencies;
    }

    public void setCurrencies(List<Currency> currencies) {
        this.currencies = currencies;
    }
}
