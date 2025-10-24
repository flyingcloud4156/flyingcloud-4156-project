package dev.coms4156.project.groupproject.controller;

import dev.coms4156.project.groupproject.dto.CreateTransactionRequest;
import dev.coms4156.project.groupproject.dto.CreateTransactionResponse;
import dev.coms4156.project.groupproject.dto.ListTransactionsResponse;
import dev.coms4156.project.groupproject.dto.Result;
import dev.coms4156.project.groupproject.dto.TransactionResponse;
import dev.coms4156.project.groupproject.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Controller for transaction-related operations. */
@RestController
@RequestMapping("/api/v1/ledgers/{ledgerId}/transactions")
@Tag(name = "Transaction APIs")
@SecurityRequirement(name = "X-Auth-Token")
public class TransactionController {

  private final TransactionService transactionService;

  @Autowired
  public TransactionController(TransactionService transactionService) {
    this.transactionService = transactionService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new transaction")
  public Result<CreateTransactionResponse> createTransaction(
      @PathVariable Long ledgerId, @Valid @RequestBody CreateTransactionRequest req) {
    return Result.ok(transactionService.createTransaction(ledgerId, req));
  }

  @GetMapping("/{transactionId}")
  @Operation(summary = "Get transaction details")
  public Result<TransactionResponse> getTransaction(
      @PathVariable Long ledgerId, @PathVariable Long transactionId) {
    return Result.ok(transactionService.getTransaction(ledgerId, transactionId));
  }

  @GetMapping
  @Operation(summary = "List transactions")
  public Result<ListTransactionsResponse> listTransactions(
      @PathVariable Long ledgerId,
      @RequestParam(value = "page", defaultValue = "1") Integer page,
      @RequestParam(value = "size", defaultValue = "50") Integer size,
      @RequestParam(value = "from", required = false) String fromDate,
      @RequestParam(value = "to", required = false) String toDate,
      @RequestParam(value = "type", required = false) String type,
      @RequestParam(value = "created_by", required = false) Long createdBy) {

    if (size > 200) {
      size = 200;
    }

    return Result.ok(
        transactionService.listTransactions(
            ledgerId, page, size, fromDate, toDate, type, createdBy));
  }

  @DeleteMapping("/{transactionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete transaction")
  public Result<Void> deleteTransaction(
      @PathVariable Long ledgerId, @PathVariable Long transactionId) {
    transactionService.deleteTransaction(ledgerId, transactionId);
    return Result.ok();
  }
}
