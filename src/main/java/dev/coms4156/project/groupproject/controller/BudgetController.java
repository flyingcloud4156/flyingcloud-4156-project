package dev.coms4156.project.groupproject.controller;

import dev.coms4156.project.groupproject.dto.BudgetStatusResponse;
import dev.coms4156.project.groupproject.dto.Result;
import dev.coms4156.project.groupproject.dto.SetBudgetRequest;
import dev.coms4156.project.groupproject.service.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller for budget-related operations. */
@RestController
@RequestMapping("/api/v1/ledgers/{ledgerId}/budgets")
@Tag(name = "Budget APIs")
@SecurityRequirement(name = "X-Auth-Token")
public class BudgetController {

  private final BudgetService budgetService;

  @Autowired
  public BudgetController(BudgetService budgetService) {
    this.budgetService = budgetService;
  }

  /**
   * Set or update a budget. If a budget with the same ledger, category, year, and month exists, it
   * will be updated; otherwise a new budget will be created.
   *
   * @param ledgerId ledger ID
   * @param request budget request
   * @return success response
   */
  @PostMapping
  @Operation(
      summary = "Set or update budget",
      description =
          "Create a new budget or update existing one. "
              + "If a budget for the same (ledger, category, year, month) exists, "
              + "it will be updated; otherwise a new budget will be created. "
              + "Requires OWNER or ADMIN role.")
  public Result<Void> setBudget(
      @Parameter(description = "Ledger ID", example = "1", required = true)
          @PathVariable("ledgerId")
          Long ledgerId,
      @Valid @RequestBody SetBudgetRequest request) {

    budgetService.setBudget(ledgerId, request);
    return Result.ok();
  }

  /**
   * Get budget status for a specific month. Returns all budgets for the ledger in the specified
   * month with spending information and alert status.
   *
   * @param ledgerId ledger ID
   * @param year year
   * @param month month (1-12)
   * @return budget status response
   */
  @GetMapping("/status")
  @Operation(
      summary = "Get budget status for a month",
      description =
          "Get all budget statuses for the specified month, including spending progress "
              + "and alert status (OK / NEAR_LIMIT / EXCEEDED).")
  public Result<BudgetStatusResponse> getBudgetStatus(
      @Parameter(description = "Ledger ID", example = "1", required = true)
          @PathVariable("ledgerId")
          Long ledgerId,
      @Parameter(description = "Year", example = "2025", required = true) @RequestParam
          Integer year,
      @Parameter(description = "Month (1-12)", example = "11", required = true) @RequestParam
          Integer month) {

    BudgetStatusResponse response = budgetService.getBudgetStatus(ledgerId, year, month);
    return Result.ok(response);
  }
}
