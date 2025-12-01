package dev.coms4156.project.groupproject.service;

import dev.coms4156.project.groupproject.dto.analytics.LedgerAnalyticsOverview;

public interface AnalyticsService {
  LedgerAnalyticsOverview overview(Long ledgerId, Integer months);
}
