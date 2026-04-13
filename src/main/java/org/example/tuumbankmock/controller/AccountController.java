package org.example.tuumbankmock.controller;

import org.example.tuumbankmock.dto.request.CreateAccountRequest;
import org.example.tuumbankmock.dto.response.CreateAccountResponse;
import org.example.tuumbankmock.dto.response.GetAccountResponse;
import org.example.tuumbankmock.service.AccountService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public CreateAccountResponse createAccount(@RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request);
    }

    @GetMapping("/{accountId}")
    public GetAccountResponse getAccountById(@PathVariable Long accountId) {
        return accountService.getAccountById(accountId);
    }
}