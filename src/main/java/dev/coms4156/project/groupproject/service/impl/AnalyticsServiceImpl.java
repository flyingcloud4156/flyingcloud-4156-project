package dev.coms4156.project.groupproject.service.impl;

import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.dto.analytics.AggRows;
import dev.coms4156.project.groupproject.dto.analytics.CategoryStat;
import dev.coms4156.project.groupproject.dto.analytics.LedgerAnalyticsOverview;
import dev.coms4156.project.groupproject.dto.analytics.MerchantStat;
import dev.coms4156.project.groupproject.dto.analytics.PeriodStat;
import dev.coms4156.project.groupproject.dto.analytics.RecommendationItem;
import dev.coms4156.project.groupproject.dto.analytics.UserArAp;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.mapper.AnalyticsAggMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import dev.coms4156.project.groupproject.service.AnalyticsService;
import dev.coms4156.project.groupproject.utils.AuthUtils;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Implementation of AnalyticsService. */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

  private final AnalyticsAggMapper aggMapper;
  private final LedgerMapper ledgerMapper;
  private final LedgerMemberMapper ledgerMemberMapper;
  private final UserMapper userMapper;

  /**
   * Constructor for AnalyticsServiceImpl.
   *
   * @param aggMapper analytics aggregation mapper
   * @param ledgerMapper ledger mapper
   * @param ledgerMemberMapper ledger member mapper
   * @param userMapper user mapper
   */
  public AnalyticsServiceImpl(
      AnalyticsAggMapper aggMapper,
      LedgerMapper ledgerMapper,
      LedgerMemberMapper ledgerMemberMapper,
      UserMapper userMapper) {
    this.aggMapper = aggMapper;
    this.ledgerMapper = ledgerMapper;
    this.ledgerMemberMapper = ledgerMemberMapper;
    this.userMapper = userMapper;
  }

  @Override
  public LedgerAnalyticsOverview overview(Long ledgerId, Integer months) {
    UserView currentUser = CurrentUserContext.get();
    if (currentUser == null) {
      throw new RuntimeException("AUTH_REQUIRED");
    }

    Ledger ledger = ledgerMapper.selectById(ledgerId);
    if (ledger == null) {
      throw new RuntimeException("LEDGER_NOT_FOUND");
    }

    LedgerMember member =
        ledgerMemberMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LedgerMember>()
                .eq(LedgerMember::getLedgerId, ledgerId)
                .eq(LedgerMember::getUserId, currentUser.getId()));
    AuthUtils.checkMembership(member != null);

    int m = months == null || months <= 0 ? 3 : Math.min(months, 24);

    YearMonth endYm = YearMonth.from(LocalDate.now());
    YearMonth startYm = endYm.minusMonths(m - 1);
    LocalDateTime start = startYm.atDay(1).atStartOfDay();
    LocalDateTime end = endYm.plusMonths(1).atDay(1).atStartOfDay();

    Long uid = currentUser.getId();

    AggRows.IncomeExpenseRow totals = aggMapper.sumIncomeExpense(ledgerId, start, end, uid);
    BigDecimal totalIncome = nz(totals.getTotalIncome());
    BigDecimal totalExpense = nz(totals.getTotalExpense());

    LedgerAnalyticsOverview out = new LedgerAnalyticsOverview();
    out.setCurrency(ledger.getBaseCurrency());
    out.setRangeStart(start);
    out.setRangeEnd(end);
    out.setTotalIncome(totalIncome);
    out.setTotalExpense(totalExpense);

    BigDecimal net = totalIncome.subtract(totalExpense);
    out.setNetBalance(net);

    List<PeriodStat> trend =
        buildContinuousTrend(startYm, endYm, aggMapper.monthlyStats(ledgerId, start, end, uid));
    out.setTrend(trend);

    List<CategoryStat> byCategory =
        buildCategoryStats(totalExpense, aggMapper.categoryStats(ledgerId, start, end, uid));
    out.setByCategory(byCategory);

    List<UserArAp> arap = buildArAp(ledgerId, uid);
    out.setArap(arap);

    List<MerchantStat> topMerchants =
        aggMapper.topMerchants(ledgerId, start, end, 5, uid).stream()
            .map(
                r -> {
                  MerchantStat ms = new MerchantStat();
                  ms.setLabel(r.getLabel());
                  ms.setAmount(nz(r.getAmount()));
                  return ms;
                })
            .collect(Collectors.toList());
    out.setTopMerchants(topMerchants);

    List<RecommendationItem> recs = new ArrayList<>();
    if (totalExpense.compareTo(totalIncome) > 0) {
      RecommendationItem item = new RecommendationItem();
      item.setCode("SPEND_TOO_HIGH");
      item.setMessage(
          "Expenses are greater than income in this period. Consider reducing spending.");
      item.setSeverity("WARNING");
      recs.add(item);
    }
    out.setRecommendations(recs);
    return out;
  }

  private List<PeriodStat> buildContinuousTrend(
      YearMonth startYm, YearMonth endYm, List<AggRows.MonthlyRow> rows) {

    Map<String, AggRows.MonthlyRow> map =
        rows.stream().collect(Collectors.toMap(AggRows.MonthlyRow::getPeriod, r -> r));

    return java.util.stream.Stream.iterate(
            startYm, ym -> !ym.isAfter(endYm), ym -> ym.plusMonths(1))
        .map(
            ym -> {
              String key = ym.toString();
              AggRows.MonthlyRow r = map.get(key);
              PeriodStat ps = new PeriodStat();
              ps.setPeriod(key);
              ps.setIncome(r == null ? BigDecimal.ZERO : nz(r.getIncome()));
              ps.setExpense(r == null ? BigDecimal.ZERO : nz(r.getExpense()));
              return ps;
            })
        .collect(Collectors.toList());
  }

  private List<CategoryStat> buildCategoryStats(
      BigDecimal totalExpense, List<AggRows.CategoryRow> rows) {

    BigDecimal denom = totalExpense == null ? BigDecimal.ZERO : totalExpense;

    return rows.stream()
        .map(
            r -> {
              CategoryStat cs = new CategoryStat();
              cs.setCategoryId(r.getCategoryId());
              cs.setCategoryName(
                  r.getCategoryName() == null || r.getCategoryName().isBlank()
                      ? "Uncategorized"
                      : r.getCategoryName());
              BigDecimal amt = nz(r.getAmount());
              cs.setAmount(amt);

              BigDecimal ratio =
                  denom.compareTo(BigDecimal.ZERO) == 0
                      ? BigDecimal.ZERO
                      : amt.divide(denom, 4, RoundingMode.HALF_UP);
              cs.setRatio(ratio);
              return cs;
            })
        .collect(Collectors.toList());
  }

  private List<UserArAp> buildArAp(Long ledgerId, Long currentUserId) {
    List<AggRows.UserAmountRow> arRows = aggMapper.arByLedger(ledgerId, currentUserId);
    List<AggRows.UserAmountRow> apRows = aggMapper.apByLedger(ledgerId, currentUserId);

    Map<Long, BigDecimal> arMap = new HashMap<>();
    for (AggRows.UserAmountRow r : arRows) {
      arMap.put(r.getUserId(), nz(r.getAmount()));
    }

    Map<Long, BigDecimal> apMap = new HashMap<>();
    for (AggRows.UserAmountRow r : apRows) {
      apMap.put(r.getUserId(), nz(r.getAmount()));
    }

    Set<Long> allUserIds = new HashSet<>();
    allUserIds.addAll(arMap.keySet());
    allUserIds.addAll(apMap.keySet());

    if (allUserIds.isEmpty()) {
      return Collections.emptyList();
    }

    List<User> users = userMapper.selectBatchIds(allUserIds);
    Map<Long, String> nameMap =
        users.stream().collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));

    List<UserArAp> out =
        allUserIds.stream()
            .map(
                uid -> {
                  UserArAp u = new UserArAp();
                  u.setUserId(uid);
                  u.setUserName(nameMap.getOrDefault(uid, "user_" + uid));
                  u.setAr(arMap.getOrDefault(uid, BigDecimal.ZERO));
                  u.setAp(apMap.getOrDefault(uid, BigDecimal.ZERO));
                  return u;
                })
            .collect(Collectors.toList());

    out.sort(
        (x, y) -> {
          BigDecimal bx = x.getAr().subtract(x.getAp());
          BigDecimal by = y.getAr().subtract(y.getAp());
          return by.compareTo(bx);
        });
    return out;
  }

  private BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
