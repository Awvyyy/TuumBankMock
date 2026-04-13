package org.example.tuumbankmock.controller;

import org.example.tuumbankmock.dto.request.CreateTransactionRequest;
import org.example.tuumbankmock.dto.response.CreateTransactionResponse;
import org.example.tuumbankmock.dto.response.GetTransactionsResponse;
import org.example.tuumbankmock.service.TransactionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public CreateTransactionResponse createTransaction(@RequestBody CreateTransactionRequest request) {
        return transactionService.createTransaction(request);
    }

    @GetMapping("/account/{accountId}")
    public GetTransactionsResponse getTransactionsByAccountId(@PathVariable Long accountId) {
        return transactionService.getTransactionsByAccountId(accountId);
    }
}