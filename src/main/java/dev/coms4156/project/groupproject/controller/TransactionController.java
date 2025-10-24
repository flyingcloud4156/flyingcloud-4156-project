package dev.coms4156.project.groupproject.controller;

import dev.coms4156.project.groupproject.dto.CreateTransactionRequest;
import dev.coms4156.project.groupproject.dto.CreateTransactionResponse;
import dev.coms4156.project.groupproject.dto.ListTransactionsResponse;
import dev.coms4156.project.groupproject.dto.Result;
import dev.coms4156.project.groupproject.dto.TransactionResponse;
import dev.coms4156.project.groupproject.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

/**
 * Controller for transaction-related operations. Handles transaction CRUD operations with split
 * calculations and debt edge generation.
 */
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

  /**
   * Create a new transaction with splits and debt edges.
   *
   * @param ledgerId ledger ID
   * @param request transaction creation request
   * @return created transaction response
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create a new transaction",
      description =
          "Create a transaction with splits and generate debt edges. "
              + "Supports EXPENSE, INCOME, and LOAN types with automatic split calculations.")
  public Result<CreateTransactionResponse> createTransaction(
      @Parameter(description = "Ledger ID", example = "456", required = true)
          @PathVariable("ledgerId")
          Long ledgerId,
      @Valid @RequestBody CreateTransactionRequest request) {

    CreateTransactionResponse response = transactionService.createTransaction(ledgerId, request);
    return Result.ok(response);
  }

  /**
   * Get transaction details by ID.
   *
   * @param ledgerId ledger ID
   * @param transactionId transaction ID
   * @return transaction details with splits and edge previews
   */
  @GetMapping("/{transactionId}")
  @Operation(
      summary = "Get transaction details",
      description = "Get detailed transaction information including splits and debt edge previews.")
  public Result<TransactionResponse> getTransaction(
      @Parameter(description = "Ledger ID", example = "456", required = true)
          @PathVariable("ledgerId")
          Long ledgerId,
      @Parameter(description = "Transaction ID", example = "1001", required = true)
          @PathVariable("transactionId")
          Long transactionId) {

    TransactionResponse response = transactionService.getTransaction(ledgerId, transactionId);
    return Result.ok(response);
  }

  /**
   * List transactions with pagination and filtering.
   *
   * @param ledgerId ledger ID
   * @param page page number (1-based, default: 1)
   * @param size page size (max: 200, default: 50)
   * @param fromDate start date filter (ISO 8601 format)
   * @param toDate end date filter (ISO 8601 format)
   * @param type transaction type filter (EXPENSE, INCOME, LOAN)
   * @param createdBy created by user ID filter
   * @return paginated transaction list
   */
  @GetMapping
  @Operation(
      summary = "List transactions",
      description =
          "Get paginated list of transactions with optional filtering by date, type, "
              + "creator, and category.")
  public Result<ListTransactionsResponse> listTransactions(
      @Parameter(description = "Ledger ID", example = "456", required = true)
          @PathVariable("ledgerId")
          Long ledgerId,
      @Parameter(description = "Page number (1-based)", example = "1")
          @RequestParam(value = "page", defaultValue = "1")
          Integer page,
      @Parameter(description = "Page size (max 200)", example = "50")
          @RequestParam(value = "size", defaultValue = "50")
          Integer size,
      @Parameter(description = "Start date filter (ISO 8601)", example = "2025-10-01T00:00:00")
          @RequestParam(value = "from", required = false)
          String fromDate,
      @Parameter(description = "End date filter (ISO 8601)", example = "2025-10-31T23:59:59")
          @RequestParam(value = "to", required = false)
          String toDate,
      @Parameter(description = "Transaction type filter", example = "EXPENSE")
          @RequestParam(value = "type", required = false)
          String type,
      @Parameter(description = "Created by user ID filter", example = "111")
          @RequestParam(value = "created_by", required = false)
          Long createdBy) {

    // Validate page size
    if (size > 200) {
      size = 200;
    }

    ListTransactionsResponse response =
        transactionService.listTransactions(
            ledgerId, page, size, fromDate, toDate, type, createdBy);
    return Result.ok(response);
  }

  /**
   * Delete a transaction and its associated splits and debt edges.
   *
   * @param ledgerId ledger ID
   * @param transactionId transaction ID
   */
  @DeleteMapping("/{transactionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete transaction",
      description =
          "Delete a transaction and all its associated splits and debt edges. "
              + "Only creator or OWNER/ADMIN can delete transactions.")
  public Result<Void> deleteTransaction(
      @Parameter(description = "Ledger ID", example = "456", required = true)
          @PathVariable("ledgerId")
          Long ledgerId,
      @Parameter(description = "Transaction ID", example = "1001", required = true)
          @PathVariable("transactionId")
          Long transactionId) {

    transactionService.deleteTransaction(ledgerId, transactionId);
    return Result.ok();
  }
}
