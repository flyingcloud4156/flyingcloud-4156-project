package dev.coms4156.project.groupproject.service;

import dev.coms4156.project.groupproject.dto.BudgetStatusResponse;
import dev.coms4156.project.groupproject.dto.SetBudgetRequest;
import java.time.LocalDateTime;

/** Service interface for budget operations. */
public interface BudgetService {

  /**
   * Set or update a budget (upsert). If a budget with the same ledger, category, year, and month
   * exists, it will be updated; otherwise a new budget will be created.
   *
   * @param ledgerId ledger ID
   * @param request budget request
   */
  void setBudget(Long ledgerId, SetBudgetRequest request);

  /**
   * Get budget status for a specific month. Returns all budgets for the ledger in the specified
   * month with spending information and alert status.
   *
   * @param ledgerId ledger ID
   * @param year year
   * @param month month (1-12)
   * @return budget status response
   */
  BudgetStatusResponse getBudgetStatus(Long ledgerId, Integer year, Integer month);

  /**
   * Check budget status after creating a transaction. Returns an alert message if budget is near
   * limit or exceeded, or null if no alert.
   *
   * @param ledgerId ledger ID
   * @param categoryId category ID (may be null)
   * @param txnAt transaction timestamp
   * @return alert message or null
   */
  String checkBudgetAfterTransaction(Long ledgerId, Long categoryId, LocalDateTime txnAt);
}
