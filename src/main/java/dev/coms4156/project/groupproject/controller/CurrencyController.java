package dev.coms4156.project.groupproject.controller;

import dev.coms4156.project.groupproject.dto.CurrencyResponse;
import dev.coms4156.project.groupproject.dto.Result;
import dev.coms4156.project.groupproject.service.CurrencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Currency APIs")
public class CurrencyController {

    private final CurrencyService currencyService;

    @Autowired
    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @GetMapping("/currencies")
    @Operation(summary = "Get all supported currencies", security = { @SecurityRequirement(name = "X-Auth-Token") })
    public Result<CurrencyResponse> getAllCurrencies() {
        return Result.ok(currencyService.getAllCurrencies());
    }
}
