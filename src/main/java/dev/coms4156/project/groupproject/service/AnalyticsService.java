package dev.coms4156.project.groupproject.service;

import dev.coms4156.project.groupproject.dto.analytics.LedgerAnalyticsOverview;

/** Service for analytics operations. */
public interface AnalyticsService {
  LedgerAnalyticsOverview overview(Long ledgerId, Integer months);
}
