package dev.coms4156.project.groupproject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.coms4156.project.groupproject.dto.BudgetStatusItem;
import dev.coms4156.project.groupproject.dto.BudgetStatusResponse;
import dev.coms4156.project.groupproject.dto.SetBudgetRequest;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.Budget;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.mapper.BudgetMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.TransactionMapper;
import dev.coms4156.project.groupproject.service.BudgetService;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of BudgetService. */
@Service
public class BudgetServiceImpl implements BudgetService {

  private static final BigDecimal THRESHOLD_NEAR = new BigDecimal("0.8");
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final BudgetMapper budgetMapper;
  private final TransactionMapper transactionMapper;
  private final LedgerMapper ledgerMapper;
  private final LedgerMemberMapper ledgerMemberMapper;

  /**
   * Constructor for BudgetServiceImpl.
   *
   * @param budgetMapper budget mapper
   * @param transactionMapper transaction mapper
   * @param ledgerMapper ledger mapper
   * @param ledgerMemberMapper ledger member mapper
   */
  @Autowired
  public BudgetServiceImpl(
      BudgetMapper budgetMapper,
      TransactionMapper transactionMapper,
      LedgerMapper ledgerMapper,
      LedgerMemberMapper ledgerMemberMapper) {
    this.budgetMapper = budgetMapper;
    this.transactionMapper = transactionMapper;
    this.ledgerMapper = ledgerMapper;
    this.ledgerMemberMapper = ledgerMemberMapper;
  }

  @Override
  @Transactional
  public void setBudget(Long ledgerId, SetBudgetRequest request) {
    UserView currentUser = CurrentUserContext.get();
    if (currentUser == null) {
      throw new RuntimeException("Not logged in");
    }

    // Validate ledger exists
    Ledger ledger = ledgerMapper.selectById(ledgerId);
    if (ledger == null) {
      throw new RuntimeException("Ledger not found");
    }

    // Validate permissions: must be OWNER or ADMIN
    LedgerMember member =
        ledgerMemberMapper.selectOne(
            new LambdaQueryWrapper<LedgerMember>()
                .eq(LedgerMember::getLedgerId, ledgerId)
                .eq(LedgerMember::getUserId, currentUser.getId()));

    if (member == null) {
      throw new RuntimeException("Not a member of this ledger");
    }

    if (!"OWNER".equals(member.getRole()) && !"ADMIN".equals(member.getRole())) {
      throw new RuntimeException("Insufficient permissions. Only OWNER or ADMIN can set budgets.");
    }

    // Query existing budget
    LambdaQueryWrapper<Budget> wrapper = new LambdaQueryWrapper<>();
    wrapper
        .eq(Budget::getLedgerId, ledgerId)
        .eq(Budget::getYear, request.getYear())
        .eq(Budget::getMonth, request.getMonth());

    if (request.getCategoryId() == null) {
      wrapper.isNull(Budget::getCategoryId);
    } else {
      wrapper.eq(Budget::getCategoryId, request.getCategoryId());
    }

    Budget existing = budgetMapper.selectOne(wrapper);

    if (existing != null) {
      // UPDATE existing budget
      existing.setLimitAmount(request.getLimitAmount());
      existing.setUpdatedAt(LocalDateTime.now());
      budgetMapper.updateById(existing);
    } else {
      // INSERT new budget
      Budget budget = new Budget();
      budget.setLedgerId(ledgerId);
      budget.setCategoryId(request.getCategoryId());
      budget.setYear(request.getYear());
      budget.setMonth(request.getMonth());
      budget.setLimitAmount(request.getLimitAmount());
      budget.setCreatedAt(LocalDateTime.now());
      budget.setUpdatedAt(LocalDateTime.now());
      budgetMapper.insert(budget);
    }
  }

  @Override
  public BudgetStatusResponse getBudgetStatus(Long ledgerId, Integer year, Integer month) {
    UserView currentUser = CurrentUserContext.get();
    if (currentUser == null) {
      throw new RuntimeException("Not logged in");
    }

    // Validate membership
    LedgerMember member =
        ledgerMemberMapper.selectOne(
            new LambdaQueryWrapper<LedgerMember>()
                .eq(LedgerMember::getLedgerId, ledgerId)
                .eq(LedgerMember::getUserId, currentUser.getId()));

    if (member == null) {
      throw new RuntimeException("Not a member of this ledger");
    }

    // Query all budgets for the specified month
    List<Budget> budgets = budgetMapper.findByLedgerAndYearMonth(ledgerId, year, month);

    // Calculate status for each budget
    List<BudgetStatusItem> items = new ArrayList<>();
    for (Budget budget : budgets) {
      BudgetStatusItem item = calculateBudgetStatus(budget, year, month);
      items.add(item);
    }

    BudgetStatusResponse response = new BudgetStatusResponse();
    response.setItems(items);
    return response;
  }

  @Override
  public String checkBudgetAfterTransaction(Long ledgerId, Long categoryId, LocalDateTime txnAt) {
    int year = txnAt.getYear();
    int month = txnAt.getMonthValue();

    // Query all budgets for the month
    List<Budget> budgets = budgetMapper.findByLedgerAndYearMonth(ledgerId, year, month);
    if (budgets.isEmpty()) {
      return null;
    }

    // Priority 1: Check category-specific budget if transaction has a category
    if (categoryId != null) {
      Budget categoryBudget = null;
      for (Budget b : budgets) {
        if (categoryId.equals(b.getCategoryId())) {
          categoryBudget = b;
          break;
        }
      }

      if (categoryBudget != null) {
        return checkAndGenerateAlert(categoryBudget, year, month);
      }
    }

    // Priority 2: Check ledger-level budget as fallback
    Budget ledgerBudget = null;
    for (Budget b : budgets) {
      if (b.getCategoryId() == null) {
        ledgerBudget = b;
        break;
      }
    }

    if (ledgerBudget != null) {
      return checkAndGenerateAlert(ledgerBudget, year, month);
    }

    return null;
  }

  /**
   * Calculate budget status for a single budget.
   *
   * @param budget budget entity
   * @param year year
   * @param month month
   * @return budget status item
   */
  private BudgetStatusItem calculateBudgetStatus(Budget budget, Integer year, Integer month) {
    LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0, 0);
    LocalDateTime end = start.plusMonths(1);

    BigDecimal spent =
        transactionMapper.sumExpenseByTimeRange(
            budget.getLedgerId(), budget.getCategoryId(), start, end);

    BigDecimal limit = budget.getLimitAmount();
    BigDecimal ratio = BigDecimal.ZERO;
    if (limit.compareTo(BigDecimal.ZERO) > 0) {
      ratio = spent.divide(limit, 4, RoundingMode.HALF_UP);
    }

    String status = determineStatus(ratio);
    String categoryName = getCategoryName(budget.getCategoryId());

    BudgetStatusItem item = new BudgetStatusItem();
    item.setBudgetId(budget.getId());
    item.setCategoryId(budget.getCategoryId());
    item.setCategoryName(categoryName);
    item.setLimitAmount(limit);
    item.setSpentAmount(spent);
    item.setRatio(ratio.toString());
    item.setStatus(status);

    return item;
  }

  /**
   * Check budget and generate alert message if needed.
   *
   * @param budget budget to check
   * @param year year
   * @param month month
   * @return alert message or null
   */
  private String checkAndGenerateAlert(Budget budget, Integer year, Integer month) {
    LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0, 0);
    LocalDateTime end = start.plusMonths(1);

    BigDecimal spent =
        transactionMapper.sumExpenseByTimeRange(
            budget.getLedgerId(), budget.getCategoryId(), start, end);

    BigDecimal limit = budget.getLimitAmount();
    if (limit.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }

    BigDecimal ratio = spent.divide(limit, 4, RoundingMode.HALF_UP);
    String status = determineStatus(ratio);

    if ("OK".equals(status)) {
      return null;
    }

    String categoryName = getCategoryName(budget.getCategoryId());
    return buildAlertMessage(categoryName, ratio, status);
  }

  /**
   * Determine budget status based on ratio.
   *
   * @param ratio spending ratio
   * @return status string
   */
  private String determineStatus(BigDecimal ratio) {
    if (ratio.compareTo(THRESHOLD_NEAR) < 0) {
      return "OK";
    } else if (ratio.compareTo(BigDecimal.ONE) < 0) {
      return "NEAR_LIMIT";
    } else {
      return "EXCEEDED";
    }
  }

  /**
   * Get category name for display. Returns "Total Budget" for null category.
   *
   * @param categoryId category ID
   * @return category name
   */
  private String getCategoryName(Long categoryId) {
    if (categoryId == null) {
      return "Total Budget";
    }
    return "Category " + categoryId;
  }

  /**
   * Build alert message based on budget status.
   *
   * @param categoryName category name
   * @param ratio usage ratio
   * @param status budget status
   * @return alert message
   */
  private String buildAlertMessage(String categoryName, BigDecimal ratio, String status) {
    int percent = ratio.multiply(HUNDRED).intValue();

    if ("EXCEEDED".equals(status)) {
      return String.format("Budget alert: %s exceeded at %d%%", categoryName, percent);
    } else {
      return String.format("Budget warning: %s at %d%%, approaching limit", categoryName, percent);
    }
  }
}
