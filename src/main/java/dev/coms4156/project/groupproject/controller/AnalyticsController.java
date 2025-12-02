package dev.coms4156.project.groupproject.controller;

import dev.coms4156.project.groupproject.dto.Result;
import dev.coms4156.project.groupproject.dto.analytics.LedgerAnalyticsOverview;
import dev.coms4156.project.groupproject.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller for analytics operations. */
@RestController
@RequestMapping("/api/v1/ledgers/{ledgerId}/analytics")
@Tag(name = "Analytics APIs")
@SecurityRequirement(name = "X-Auth-Token")
public class AnalyticsController {

  private final AnalyticsService analyticsService;

  public AnalyticsController(AnalyticsService analyticsService) {
    this.analyticsService = analyticsService;
  }

  @GetMapping("/overview")
  @Operation(
      summary = "Ledger analytics overview",
      description =
          "Provides comprehensive overview including income/expense, trend, "
              + "category breakdown, AR/AP, merchants, and recommendations.")
  public Result<LedgerAnalyticsOverview> overview(
      @PathVariable Long ledgerId,
      @RequestParam(value = "months", required = false, defaultValue = "3") Integer months) {
    return Result.ok(analyticsService.overview(ledgerId, months));
  }
}
