package dev.coms4156.project.groupproject.service;

import dev.coms4156.project.groupproject.dto.CreateTransactionRequest;
import dev.coms4156.project.groupproject.dto.CreateTransactionResponse;
import dev.coms4156.project.groupproject.dto.ListTransactionsResponse;
import dev.coms4156.project.groupproject.dto.TransactionResponse;
import dev.coms4156.project.groupproject.dto.UpdateTransactionRequest;
import dev.coms4156.project.groupproject.dto.UpdateTransactionResponse;

/**
 * Service interface for transaction operations. Handles transaction creation, updates, queries, and
 * debt edge generation.
 */
public interface TransactionService {

  /**
   * Create a new transaction with splits and debt edges.
   *
   * @param ledgerId ledger ID
   * @param request transaction creation request
   * @return created transaction response
   */
  CreateTransactionResponse createTransaction(Long ledgerId, CreateTransactionRequest request);

  /**
   * Get transaction details by ID.
   *
   * @param ledgerId ledger ID
   * @param transactionId transaction ID
   * @return transaction details
   */
  TransactionResponse getTransaction(Long ledgerId, Long transactionId);

  /**
   * List transactions with pagination and filtering.
   *
   * @param ledgerId ledger ID
   * @param page page number (1-based)
   * @param size page size
   * @param fromDate start date filter (ISO 8601)
   * @param toDate end date filter (ISO 8601)
   * @param type transaction type filter
   * @param createdBy created by user ID filter
   * @param categoryId category ID filter
   * @return paginated transaction list
   */
  ListTransactionsResponse listTransactions(
      Long ledgerId,
      Integer page,
      Integer size,
      String fromDate,
      String toDate,
      String type,
      Long createdBy,
      Long categoryId);

  /**
   * Update an existing transaction.
   *
   * @param ledgerId ledger ID
   * @param transactionId transaction ID
   * @param request update request
   * @return update response
   */
  UpdateTransactionResponse updateTransaction(
      Long ledgerId, Long transactionId, UpdateTransactionRequest request);

  /**
   * Delete a transaction and its associated splits and debt edges.
   *
   * @param ledgerId ledger ID
   * @param transactionId transaction ID
   */
  void deleteTransaction(Long ledgerId, Long transactionId);
}
