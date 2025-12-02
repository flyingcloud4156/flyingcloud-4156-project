package dev.coms4156.project.groupproject.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.dto.analytics.AggRows;
import dev.coms4156.project.groupproject.dto.analytics.LedgerAnalyticsOverview;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.mapper.AnalyticsAggMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AnalyticsServiceImpl}.
 *
 * <p>Focus:
 *
 * <ul>
 *   <li>Authorization and membership guards (AUTH_REQUIRED, LEDGER_NOT_FOUND, membership check)
 *   <li>months input partitions & boundary behavior (null, <=0, 1, >24)
 *   <li>Trend continuity (fill missing months with zeros)
 *   <li>Category ratio rules (denom zero, blank category name -> Uncategorized)
 *   <li>AR/AP merge + sorting by net (AR-AP) and empty behavior
 *   <li>Recommendations (expense > income triggers warning; otherwise none)
 * </ul>
 *
 * <p>External dependencies are mocked (mappers). No DB/Redis/HTTP is used.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

  @Mock private AnalyticsAggMapper aggMapper;
  @Mock private LedgerMapper ledgerMapper;
  @Mock private LedgerMemberMapper ledgerMemberMapper;
  @Mock private UserMapper userMapper;

  private AnalyticsServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new AnalyticsServiceImpl(aggMapper, ledgerMapper, ledgerMemberMapper, userMapper);
  }

  @AfterEach
  void tearDown() {
    // Ensure isolation across tests (ThreadLocal context).
    try {
      CurrentUserContext.clear();
    } catch (Throwable ignored) {
      // If clear() does not exist in your context impl, replace with remove() accordingly.
    }
  }

  private static UserView user(long id) {
    UserView uv = new UserView();
    uv.setId(id);
    uv.setName("u" + id);
    return uv;
  }

  private static Ledger ledger(long id, String currency) {
    Ledger l = new Ledger();
    l.setId(id);
    l.setBaseCurrency(currency);
    l.setName("L" + id);
    l.setOwnerId(1L);
    l.setLedgerType("GROUP_BALANCE");
    return l;
  }

  private static AggRows.IncomeExpenseRow totals(String income, String expense) {
    AggRows.IncomeExpenseRow row = new AggRows.IncomeExpenseRow();
    row.setTotalIncome(new BigDecimal(income));
    row.setTotalExpense(new BigDecimal(expense));
    return row;
  }

  private static AggRows.MonthlyRow mrow(String period, String income, String expense) {
    AggRows.MonthlyRow r = new AggRows.MonthlyRow();
    r.setPeriod(period);
    r.setIncome(new BigDecimal(income));
    r.setExpense(new BigDecimal(expense));
    return r;
  }

  private static AggRows.CategoryRow crow(Long cid, String name, String amount) {
    AggRows.CategoryRow r = new AggRows.CategoryRow();
    r.setCategoryId(cid);
    r.setCategoryName(name);
    r.setAmount(new BigDecimal(amount));
    return r;
  }

  private static AggRows.MerchantRow merch(String label, String amount) {
    AggRows.MerchantRow r = new AggRows.MerchantRow();
    r.setLabel(label);
    r.setAmount(new BigDecimal(amount));
    return r;
  }

  private static AggRows.UserAmountRow uar(long uid, String amount) {
    AggRows.UserAmountRow r = new AggRows.UserAmountRow();
    r.setUserId(uid);
    r.setAmount(new BigDecimal(amount));
    return r;
  }

  @Test
  void givenNoCurrentUser_whenOverview_thenThrowsAuthRequired() {
    // Given: no CurrentUserContext set
    Long ledgerId = 1L;

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.overview(ledgerId, 3));

    assertEquals("AUTH_REQUIRED", ex.getMessage());
    verifyNoInteractions(ledgerMapper, ledgerMemberMapper, aggMapper, userMapper);
  }

  @Test
  void givenLedgerNotFound_whenOverview_thenThrowsLedgerNotFound() {
    // Given
    CurrentUserContext.set(user(10L));
    when(ledgerMapper.selectById(99L)).thenReturn(null);

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.overview(99L, 3));

    assertEquals("LEDGER_NOT_FOUND", ex.getMessage());
    verify(ledgerMapper).selectById(99L);
    verifyNoMoreInteractions(ledgerMapper);
    verifyNoInteractions(ledgerMemberMapper, aggMapper, userMapper);
  }

  @Test
  void givenNotMember_whenOverview_thenThrows() {
    // Given
    CurrentUserContext.set(user(10L));
    when(ledgerMapper.selectById(1L)).thenReturn(ledger(1L, "USD"));
    when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

    // When / Then: AuthUtils.checkMembership(false) should throw RuntimeException
    assertThrows(RuntimeException.class, () -> service.overview(1L, 3));

    verify(ledgerMapper).selectById(1L);
    verify(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));
    verifyNoInteractions(aggMapper, userMapper);
  }

  @Test
  void givenMonthsNullOrNonPositive_whenOverview_thenDefaultsTo3AndBuildsContinuousTrend() {
    // Given
    CurrentUserContext.set(user(10L));
    when(ledgerMapper.selectById(1L)).thenReturn(ledger(1L, "USD"));
    when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
        .thenReturn(new LedgerMember());

    // Totals: make expense > income to trigger a recommendation.
    when(aggMapper.sumIncomeExpense(eq(1L), any(), any(), eq(10L)))
        .thenReturn(totals("10.00", "20.00"));

    // Build a sparse monthly list: only include the middle month; service must fill other months
    // with zeros.
    LocalDate today = LocalDate.now();
    YearMonth endYm = YearMonth.from(today);
    YearMonth startYm = endYm.minusMonths(2); // default 3 months => start = end-2
    YearMonth midYm = startYm.plusMonths(1);

    when(aggMapper.monthlyStats(eq(1L), any(), any(), eq(10L)))
        .thenReturn(List.of(mrow(midYm.toString(), "5.00", "7.00")));

    when(aggMapper.categoryStats(eq(1L), any(), any(), eq(10L)))
        .thenReturn(List.of(crow(1L, "Food", "20.00")));

    when(aggMapper.topMerchants(eq(1L), any(), any(), eq(5), eq(10L)))
        .thenReturn(List.of(merch("M1", "20.00")));

    when(aggMapper.arByLedger(eq(1L), eq(10L))).thenReturn(List.of());
    when(aggMapper.apByLedger(eq(1L), eq(10L))).thenReturn(List.of());

    // When: months = null -> default 3
    LedgerAnalyticsOverview out1 = service.overview(1L, null);

    // Then: trend should include exactly 3 months: startYm, midYm, endYm
    assertNotNull(out1.getTrend());
    assertEquals(3, out1.getTrend().size());

    assertEquals(startYm.toString(), out1.getTrend().get(0).getPeriod());
    assertEquals("0", out1.getTrend().get(0).getIncome().toPlainString());
    assertEquals("0", out1.getTrend().get(0).getExpense().toPlainString());

    assertEquals(midYm.toString(), out1.getTrend().get(1).getPeriod());
    assertEquals("5.00", out1.getTrend().get(1).getIncome().toPlainString());
    assertEquals("7.00", out1.getTrend().get(1).getExpense().toPlainString());

    assertEquals(endYm.toString(), out1.getTrend().get(2).getPeriod());
    assertEquals("0", out1.getTrend().get(2).getIncome().toPlainString());
    assertEquals("0", out1.getTrend().get(2).getExpense().toPlainString());

    // And: expense > income triggers a recommendation
    assertNotNull(out1.getRecommendations());
    assertEquals(1, out1.getRecommendations().size());
    assertEquals("SPEND_TOO_HIGH", out1.getRecommendations().get(0).getCode());
    assertEquals("WARNING", out1.getRecommendations().get(0).getSeverity());

    // When: months <= 0 -> also default 3
    LedgerAnalyticsOverview out2 = service.overview(1L, 0);
    assertEquals(3, out2.getTrend().size());
  }

  @Test
  void givenMonthsAbove24_whenOverview_thenCapsTo24() {
    // Given
    CurrentUserContext.set(user(10L));
    when(ledgerMapper.selectById(2L)).thenReturn(ledger(2L, "USD"));
    when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
        .thenReturn(new LedgerMember());

    when(aggMapper.sumIncomeExpense(eq(2L), any(), any(), eq(10L)))
        .thenReturn(totals("0.00", "0.00"));
    when(aggMapper.monthlyStats(eq(2L), any(), any(), eq(10L))).thenReturn(List.of());
    when(aggMapper.categoryStats(eq(2L), any(), any(), eq(10L))).thenReturn(List.of());
    when(aggMapper.topMerchants(eq(2L), any(), any(), eq(5), eq(10L))).thenReturn(List.of());
    when(aggMapper.arByLedger(eq(2L), eq(10L))).thenReturn(List.of());
    when(aggMapper.apByLedger(eq(2L), eq(10L))).thenReturn(List.of());

    ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
    ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);

    // When
    service.overview(2L, 999);

    // Then: verify time range corresponds to 24 months window
    verify(aggMapper).sumIncomeExpense(eq(2L), startCap.capture(), endCap.capture(), eq(10L));

    LocalDate today = LocalDate.now();
    YearMonth endYm = YearMonth.from(today);
    YearMonth expectedStartYm = endYm.minusMonths(23); // cap=24 => start = end - (24-1)

    assertEquals(expectedStartYm.atDay(1).atStartOfDay(), startCap.getValue());
    assertEquals(endYm.plusMonths(1).atDay(1).atStartOfDay(), endCap.getValue());
  }

  @Test
  void givenCategoryNameBlankAndZeroExpense_whenOverview_thenUsesUncategorizedAndZeroRatios() {
    // Given
    CurrentUserContext.set(user(10L));
    when(ledgerMapper.selectById(3L)).thenReturn(ledger(3L, "USD"));
    when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
        .thenReturn(new LedgerMember());

    // totalExpense=0 => ratio must be 0 for all categories (avoid division-by-zero).
    when(aggMapper.sumIncomeExpense(eq(3L), any(), any(), eq(10L)))
        .thenReturn(totals("0.00", "0.00"));
    when(aggMapper.monthlyStats(eq(3L), any(), any(), eq(10L))).thenReturn(List.of());

    when(aggMapper.categoryStats(eq(3L), any(), any(), eq(10L)))
        .thenReturn(List.of(crow(1L, "   ", "12.34"), crow(2L, null, "1.00")));

    when(aggMapper.topMerchants(eq(3L), any(), any(), eq(5), eq(10L))).thenReturn(List.of());
    when(aggMapper.arByLedger(eq(3L), eq(10L))).thenReturn(List.of());
    when(aggMapper.apByLedger(eq(3L), eq(10L))).thenReturn(List.of());

    // When
    LedgerAnalyticsOverview out = service.overview(3L, 1);

    // Then
    assertEquals(2, out.getByCategory().size());

    assertEquals("Uncategorized", out.getByCategory().get(0).getCategoryName());
    assertEquals(new BigDecimal("12.34"), out.getByCategory().get(0).getAmount());
    assertEquals(BigDecimal.ZERO, out.getByCategory().get(0).getRatio());

    assertEquals("Uncategorized", out.getByCategory().get(1).getCategoryName());
    assertEquals(new BigDecimal("1.00"), out.getByCategory().get(1).getAmount());
    assertEquals(BigDecimal.ZERO, out.getByCategory().get(1).getRatio());
  }

  @Test
  void givenExpenseNotGreaterThanIncome_whenOverview_thenNoRecommendations() {
    // Given
    CurrentUserContext.set(user(10L));
    when(ledgerMapper.selectById(4L)).thenReturn(ledger(4L, "USD"));
    when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
        .thenReturn(new LedgerMember());

    // expense <= income => no SPEND_TOO_HIGH
    when(aggMapper.sumIncomeExpense(eq(4L), any(), any(), eq(10L)))
        .thenReturn(totals("100.00", "99.99"));
    when(aggMapper.monthlyStats(eq(4L), any(), any(), eq(10L))).thenReturn(List.of());
    when(aggMapper.categoryStats(eq(4L), any(), any(), eq(10L))).thenReturn(List.of());
    when(aggMapper.topMerchants(eq(4L), any(), any(), eq(5), eq(10L))).thenReturn(List.of());
    when(aggMapper.arByLedger(eq(4L), eq(10L))).thenReturn(List.of());
    when(aggMapper.apByLedger(eq(4L), eq(10L))).thenReturn(List.of());

    // When
    LedgerAnalyticsOverview out = service.overview(4L, 3);

    // Then
    assertNotNull(out.getRecommendations());
    assertTrue(
        out.getRecommendations().isEmpty(), "Expected no recommendations when expense <= income");
  }

  @Test
  void givenArApRows_whenOverview_thenMergesNamesAndSortsByNetDescending() {
    // Given
    CurrentUserContext.set(user(10L));
    when(ledgerMapper.selectById(5L)).thenReturn(ledger(5L, "USD"));
    when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
        .thenReturn(new LedgerMember());

    when(aggMapper.sumIncomeExpense(eq(5L), any(), any(), eq(10L)))
        .thenReturn(totals("0.00", "1.00"));
    when(aggMapper.monthlyStats(eq(5L), any(), any(), eq(10L))).thenReturn(List.of());
    when(aggMapper.categoryStats(eq(5L), any(), any(), eq(10L))).thenReturn(List.of());
    when(aggMapper.topMerchants(eq(5L), any(), any(), eq(5), eq(10L))).thenReturn(List.of());

    // AR: user 1 -> 10, user 2 -> 5
    when(aggMapper.arByLedger(eq(5L), eq(10L)))
        .thenReturn(List.of(uar(1L, "10.00"), uar(2L, "5.00")));

    // AP: user 1 -> 1, user 3 -> 8
    when(aggMapper.apByLedger(eq(5L), eq(10L)))
        .thenReturn(List.of(uar(1L, "1.00"), uar(3L, "8.00")));

    // name map for {1,2,3}
    User u1 = new User();
    u1.setId(1L);
    u1.setName("Alice");
    User u2 = new User();
    u2.setId(2L);
    u2.setName("Bob");
    User u3 = new User();
    u3.setId(3L);
    u3.setName("Charlie");

    when(userMapper.selectBatchIds(argThat((Set<Long> ids) -> ids.containsAll(Set.of(1L, 2L, 3L)))))
        .thenReturn(List.of(u1, u2, u3));

    // When
    LedgerAnalyticsOverview out = service.overview(5L, 3);

    // Then: net = AR - AP
    // user1 net=9, user2 net=5, user3 net=-8
    assertEquals(3, out.getArap().size());
    assertEquals("Alice", out.getArap().get(0).getUserName());
    assertEquals(new BigDecimal("10.00"), out.getArap().get(0).getAr());
    assertEquals(new BigDecimal("1.00"), out.getArap().get(0).getAp());

    assertEquals("Bob", out.getArap().get(1).getUserName());
    assertEquals(new BigDecimal("5.00"), out.getArap().get(1).getAr());
    assertEquals(BigDecimal.ZERO, out.getArap().get(1).getAp());

    assertEquals("Charlie", out.getArap().get(2).getUserName());
    assertEquals(BigDecimal.ZERO, out.getArap().get(2).getAr());
    assertEquals(new BigDecimal("8.00"), out.getArap().get(2).getAp());
  }
}
