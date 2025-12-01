package dev.coms4156.project.groupproject.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.coms4156.project.groupproject.dto.Result;
import dev.coms4156.project.groupproject.dto.analytics.LedgerAnalyticsOverview;
import dev.coms4156.project.groupproject.service.AnalyticsService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AnalyticsController}.
 *
 * <p>These tests do not start Spring MVC. They validate:
 *
 * <ul>
 *   <li>Controller delegates to {@link AnalyticsService} with correct parameters
 *   <li>Returned {@link Result} wraps service output
 * </ul>
 *
 * <p>External dependencies (service) are mocked.
 */
class AnalyticsControllerUnitTest {

  @Test
  void givenMonthsProvided_whenOverview_thenDelegatesWithSameMonths() {
    AnalyticsService svc = mock(AnalyticsService.class);
    AnalyticsController controller = new AnalyticsController(svc);

    LedgerAnalyticsOverview out = new LedgerAnalyticsOverview();
    out.setCurrency("USD");
    out.setTotalExpense(new BigDecimal("123.45"));
    when(svc.overview(7L, 9)).thenReturn(out);

    Result<LedgerAnalyticsOverview> resp = controller.overview(7L, 9);

    assertTrue(resp.isSuccess());
    assertNotNull(resp.getData());
    assertEquals("USD", resp.getData().getCurrency());
    assertEquals(new BigDecimal("123.45"), resp.getData().getTotalExpense());

    verify(svc).overview(7L, 9);
    verifyNoMoreInteractions(svc);
  }

  @Test
  void givenMonthsNull_whenOverview_thenDelegatesWithNull_controllerDoesNotOverrideDefault() {
    AnalyticsService svc = mock(AnalyticsService.class);
    AnalyticsController controller = new AnalyticsController(svc);

    LedgerAnalyticsOverview out = new LedgerAnalyticsOverview();
    when(svc.overview(1L, null)).thenReturn(out);

    // Note: @RequestParam defaultValue is enforced by Spring binding,
    // but in a direct unit call, null stays null.
    Result<LedgerAnalyticsOverview> resp = controller.overview(1L, null);

    assertTrue(resp.isSuccess());
    verify(svc).overview(1L, null);
  }
}
