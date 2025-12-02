package dev.coms4156.project.groupproject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.coms4156.project.groupproject.dto.AddLedgerMemberRequest;
import dev.coms4156.project.groupproject.dto.CreateCategoryRequest;
import dev.coms4156.project.groupproject.dto.CreateLedgerRequest;
import dev.coms4156.project.groupproject.dto.LedgerMemberResponse;
import dev.coms4156.project.groupproject.dto.LedgerResponse;
import dev.coms4156.project.groupproject.dto.ListLedgerMembersResponse;
import dev.coms4156.project.groupproject.dto.MyLedgersResponse;
import dev.coms4156.project.groupproject.dto.SettlementConfig;
import dev.coms4156.project.groupproject.dto.SettlementPlanResponse;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.*;
import dev.coms4156.project.groupproject.mapper.*;
import dev.coms4156.project.groupproject.service.LedgerService;
import dev.coms4156.project.groupproject.utils.AuthUtils;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of the LedgerService interface. */
@Service
public class LedgerServiceImpl extends ServiceImpl<LedgerMapper, Ledger> implements LedgerService {

  private final LedgerMemberMapper ledgerMemberMapper;
  private final UserMapper userMapper;
  private final DebtEdgeMapper debtEdgeMapper;
  private final CurrencyMapper currencyMapper;
  //  categoryMapper
  private final CategoryMapper categoryMapper;

  /**
   * Constructor for LedgerServiceImpl.
   *
   * @param ledgerMemberMapper ledger member mapper
   * @param userMapper user mapper
   * @param debtEdgeMapper debt edge mapper
   * @param currencyMapper currency mapper
   * @param categoryMapper category mapper
   */
  @Autowired
  public LedgerServiceImpl(
      LedgerMemberMapper ledgerMemberMapper,
      UserMapper userMapper,
      DebtEdgeMapper debtEdgeMapper,
      CurrencyMapper currencyMapper,
      CategoryMapper categoryMapper) {
    this.ledgerMemberMapper = ledgerMemberMapper;
    this.userMapper = userMapper;
    this.debtEdgeMapper = debtEdgeMapper;
    this.currencyMapper = currencyMapper;
    this.categoryMapper = categoryMapper;
  }

  @Override
  @Transactional
  public LedgerResponse createLedger(CreateLedgerRequest req) {
    UserView currentUser = CurrentUserContext.get();
    if (currentUser == null) {
      throw new RuntimeException("AUTH_REQUIRED");
    }

    Ledger ledger = new Ledger();
    ledger.setName(req.getName());
    ledger.setLedgerType(req.getLedgerType());
    ledger.setBaseCurrency(req.getBaseCurrency());
    ledger.setShareStartDate(req.getShareStartDate());
    ledger.setOwnerId(currentUser.getId());
    save(ledger);

    // Create category from the request
    createCategoryFromRequest(ledger.getId(), req.getCategory());

    LedgerMember member = new LedgerMember();
    member.setLedgerId(ledger.getId());
    member.setUserId(currentUser.getId());
    member.setRole("OWNER");
    ledgerMemberMapper.insert(member);

    return new LedgerResponse(
        ledger.getId(),
        ledger.getName(),
        ledger.getLedgerType(),
        ledger.getBaseCurrency(),
        ledger.getShareStartDate(),
        member.getRole());
  }

  @Override
  public MyLedgersResponse getMyLedgers() {
    UserView currentUser = CurrentUserContext.get();
    if (currentUser == null) {
      throw new RuntimeException("AUTH_REQUIRED");
    }

    List<LedgerMember> memberships =
        ledgerMemberMapper.selectList(
            new LambdaQueryWrapper<LedgerMember>()
                .eq(LedgerMember::getUserId, currentUser.getId()));

    List<MyLedgersResponse.LedgerItem> items =
        memberships.stream()
            .map(
                m -> {
                  Ledger ledger = getById(m.getLedgerId());
                  return new MyLedgersResponse.LedgerItem(
                      ledger.getId(),
                      ledger.getName(),
                      ledger.getLedgerType(),
                      ledger.getBaseCurrency(),
                      m.getRole());
                })
            .collect(Collectors.toList());

    return new MyLedgersResponse(items);
  }

  @Override
  public LedgerResponse getLedgerDetails(Long ledgerId) {
    UserView currentUser = CurrentUserContext.get();
    if (currentUser == null) {
      throw new RuntimeException("AUTH_REQUIRED");
    }

    Ledger ledger = getById(ledgerId);
    if (ledger == null) {
      throw new RuntimeException("LEDGER_NOT_FOUND");
    }

    LedgerMember member = getLedgerMember(ledgerId, currentUser.getId());

    AuthUtils.checkMembership(member != null);

    return new LedgerResponse(
        ledger.getId(),
        ledger.getName(),
        ledger.getLedgerType(),
        ledger.getBaseCurrency(),
        ledger.getShareStartDate(),
        member.getRole());
  }

  @Override
  @Transactional
  public LedgerMemberResponse addMember(Long ledgerId, AddLedgerMemberRequest req) {

    UserView currentUser = CurrentUserContext.get();

    if (currentUser == null) {

      throw new RuntimeException("AUTH_REQUIRED");
    }

    LedgerMember callingUserMember = getLedgerMember(ledgerId, currentUser.getId());

    AuthUtils.checkRole(callingUserMember, "OWNER", "ADMIN");

    LedgerMember existingMember = getLedgerMember(ledgerId, req.getUserId());

    if (existingMember != null) {

      return new LedgerMemberResponse(ledgerId, req.getUserId(), existingMember.getRole());
    }

    LedgerMember newMember = new LedgerMember();

    newMember.setLedgerId(ledgerId);

    newMember.setUserId(req.getUserId());

    newMember.setRole(req.getRole());

    ledgerMemberMapper.insert(newMember);

    return new LedgerMemberResponse(ledgerId, req.getUserId(), req.getRole());
  }

  @Override
  public ListLedgerMembersResponse listMembers(Long ledgerId) {

    UserView currentUser = CurrentUserContext.get();

    if (currentUser == null) {

      throw new RuntimeException("AUTH_REQUIRED");
    }

    AuthUtils.checkMembership(isMember(ledgerId, currentUser.getId()));

    List<LedgerMember> members =
        ledgerMemberMapper.selectList(
            new LambdaQueryWrapper<LedgerMember>().eq(LedgerMember::getLedgerId, ledgerId));

    List<ListLedgerMembersResponse.LedgerMemberItem> items =
        members.stream()
            .map(
                m -> {
                  User user = userMapper.selectById(m.getUserId());

                  return new ListLedgerMembersResponse.LedgerMemberItem(
                      m.getUserId(), user.getName(), m.getRole());
                })
            .collect(Collectors.toList());

    return new ListLedgerMembersResponse(items);
  }

  @Override
  @Transactional
  public void removeMember(Long ledgerId, Long userId) {

    UserView currentUser = CurrentUserContext.get();

    if (currentUser == null) {

      throw new RuntimeException("AUTH_REQUIRED");
    }

    LedgerMember callingUserMember = getLedgerMember(ledgerId, currentUser.getId());

    AuthUtils.checkRole(callingUserMember, "OWNER", "ADMIN");

    if (currentUser.getId().equals(userId)) {
      long ownerCount =
          ledgerMemberMapper.selectCount(
              new LambdaQueryWrapper<LedgerMember>()
                  .eq(LedgerMember::getLedgerId, ledgerId)
                  .eq(LedgerMember::getRole, "OWNER"));
      if (ownerCount <= 1) {
        throw new RuntimeException("CANNOT_REMOVE_LAST_OWNER");
      }
    }

    ledgerMemberMapper.delete(
        new LambdaQueryWrapper<LedgerMember>()
            .eq(LedgerMember::getLedgerId, ledgerId)
            .eq(LedgerMember::getUserId, userId));
  }

  private LedgerMember getLedgerMember(Long ledgerId, Long userId) {
    return ledgerMemberMapper.selectOne(
        new LambdaQueryWrapper<LedgerMember>()
            .eq(LedgerMember::getLedgerId, ledgerId)
            .eq(LedgerMember::getUserId, userId));
  }

  private boolean isMember(Long ledgerId, Long userId) {
    return getLedgerMember(ledgerId, userId) != null;
  }

  @Override
  public SettlementPlanResponse getSettlementPlan(Long ledgerId) {
    return getSettlementPlan(ledgerId, null);
  }

  /**
   * Get settlement plan with optional configuration for constraints, rounding, and algorithm
   * selection.
   *
   * @param ledgerId ledger ID
   * @param config optional settlement configuration
   * @return settlement plan response
   */
  public SettlementPlanResponse getSettlementPlan(Long ledgerId, SettlementConfig config) {
    UserView currentUser = CurrentUserContext.get();
    if (currentUser == null) {
      throw new RuntimeException("AUTH_REQUIRED");
    }

    Ledger ledger = getById(ledgerId);
    if (ledger == null) {
      throw new RuntimeException("LEDGER_NOT_FOUND");
    }

    AuthUtils.checkMembership(isMember(ledgerId, currentUser.getId()));

    // Use default config if not provided
    if (config == null) {
      config = new SettlementConfig();
    }

    List<DebtEdge> allEdges = debtEdgeMapper.findByLedgerId(ledgerId);

    // Calculate net balances with currency conversion
    Map<Long, BigDecimal> netBalances =
        calculateNetBalances(allEdges, ledger.getBaseCurrency(), config);

    // Generate settlement plan with constraints
    List<SettlementPlanResponse.TransferItem> transfers =
        generateSettlementPlan(netBalances, config, ledger.getBaseCurrency());

    return new SettlementPlanResponse(
        ledgerId, ledger.getBaseCurrency(), transfers.size(), transfers);
  }

  /**
   * Calculate net balances from debt edges, handling symmetry and currency conversion. If A owes B
   * 20 and B owes A 40, the net becomes B owes A 20. Converts all amounts to base currency.
   *
   * @param edges all debt edges for the ledger
   * @param baseCurrency target currency for conversion
   * @param config settlement configuration with currency rates
   * @return map of user ID to net balance (positive = creditor, negative = debtor) in base currency
   */
  private Map<Long, BigDecimal> calculateNetBalances(
      List<DebtEdge> edges, String baseCurrency, SettlementConfig config) {
    Map<Long, BigDecimal> balances = new HashMap<>();

    for (DebtEdge edge : edges) {
      Long creditorId = edge.getFromUserId();
      Long debtorId = edge.getToUserId();
      BigDecimal amount = edge.getAmount();

      // Convert to base currency if needed
      String edgeCurrency = edge.getEdgeCurrency() != null ? edge.getEdgeCurrency() : baseCurrency;
      if (!edgeCurrency.equals(baseCurrency)) {
        amount = convertCurrency(amount, edgeCurrency, baseCurrency, config);
      }

      balances.put(creditorId, balances.getOrDefault(creditorId, BigDecimal.ZERO).add(amount));
      balances.put(debtorId, balances.getOrDefault(debtorId, BigDecimal.ZERO).subtract(amount));
    }

    return balances;
  }

  /**
   * Convert amount from source currency to target currency using rates from config or 1:1 if same
   * currency.
   *
   * @param amount amount to convert
   * @param fromCurrency source currency
   * @param toCurrency target currency
   * @param config settlement config with currency rates
   * @return converted amount
   */
  private BigDecimal convertCurrency(
      BigDecimal amount, String fromCurrency, String toCurrency, SettlementConfig config) {
    if (fromCurrency.equals(toCurrency)) {
      return amount;
    }

    // Check config for conversion rate
    if (config != null && config.getCurrencyRates() != null) {
      String rateKey = fromCurrency + "-" + toCurrency;
      BigDecimal rate = config.getCurrencyRates().get(rateKey);
      if (rate != null) {
        return amount.multiply(rate);
      }

      // Try reverse rate
      String reverseKey = toCurrency + "-" + fromCurrency;
      BigDecimal reverseRate = config.getCurrencyRates().get(reverseKey);
      if (reverseRate != null) {
        return amount.divide(reverseRate, 8, RoundingMode.HALF_UP);
      }
    }

    // Default: assume 1:1 (or throw error for production)
    // For now, we'll use 1:1 as fallback
    return amount;
  }

  /**
   * Generate settlement plan using heap-greedy or min-cost flow algorithm based on config. Applies
   * rounding, caps, and payment channel constraints.
   *
   * @param netBalances map of user ID to net balance
   * @param config settlement configuration
   * @param baseCurrency currency for rounding
   * @return list of transfer instructions (who pays whom how much)
   */
  private List<SettlementPlanResponse.TransferItem> generateSettlementPlan(
      Map<Long, BigDecimal> netBalances, SettlementConfig config, String baseCurrency) {

    // Check if we should use min-cost flow
    boolean useMinCostFlow = false;
    if (config.getForceMinCostFlow() != null && config.getForceMinCostFlow()) {
      useMinCostFlow = true;
    }

    List<SettlementPlanResponse.TransferItem> transfers;

    if (useMinCostFlow) {
      transfers = generateMinCostFlowSettlement(netBalances, config, baseCurrency);
    } else {
      transfers = generateHeapGreedySettlement(netBalances, config, baseCurrency);

      // Check if we should fallback to min-cost flow
      if (config.getMinCostFlowThreshold() != null
          && transfers.size() > config.getMinCostFlowThreshold()) {
        List<SettlementPlanResponse.TransferItem> minCostTransfers =
            generateMinCostFlowSettlement(netBalances, config, baseCurrency);
        if (minCostTransfers.size() < transfers.size()) {
          transfers = minCostTransfers;
        }
      }
    }

    return transfers;
  }

  /**
   * Generate settlement plan using heap-greedy algorithm. Matches largest creditors with largest
   * debtors to minimize number of transfers.
   *
   * @param netBalances map of user ID to net balance
   * @param config settlement configuration
   * @param baseCurrency currency for rounding
   * @return list of transfer instructions
   */
  private List<SettlementPlanResponse.TransferItem> generateHeapGreedySettlement(
      Map<Long, BigDecimal> netBalances, SettlementConfig config, String baseCurrency) {

    List<SettlementPlanResponse.TransferItem> transfers = new ArrayList<>();

    PriorityQueue<BalanceEntry> creditors =
        new PriorityQueue<>((a, b) -> b.getAmount().compareTo(a.getAmount()));
    PriorityQueue<BalanceEntry> debtors =
        new PriorityQueue<>((a, b) -> a.getAmount().compareTo(b.getAmount()));

    Map<Long, User> userCache = new HashMap<>();

    for (Map.Entry<Long, BigDecimal> entry : netBalances.entrySet()) {
      BigDecimal balance = entry.getValue();
      if (balance.compareTo(BigDecimal.ZERO) > 0) {
        User user = userCache.computeIfAbsent(entry.getKey(), userMapper::selectById);
        if (user != null) {
          creditors.offer(new BalanceEntry(entry.getKey(), user.getName(), balance));
        }
      } else if (balance.compareTo(BigDecimal.ZERO) < 0) {
        User user = userCache.computeIfAbsent(entry.getKey(), userMapper::selectById);
        if (user != null) {
          debtors.offer(new BalanceEntry(entry.getKey(), user.getName(), balance.abs()));
        }
      }
    }

    processSettlementsWithConstraints(creditors, debtors, transfers, config, baseCurrency);
    return transfers;
  }

  /**
   * Generate settlement plan using min-cost flow algorithm (simplified implementation). Uses
   * Edmonds-Karp style approach for minimal transfers.
   *
   * @param netBalances map of user ID to net balance
   * @param config settlement configuration
   * @param baseCurrency currency for rounding
   * @return list of transfer instructions
   */
  private List<SettlementPlanResponse.TransferItem> generateMinCostFlowSettlement(
      Map<Long, BigDecimal> netBalances, SettlementConfig config, String baseCurrency) {

    // Simplified min-cost flow: create a flow network and find minimal cost matching
    // For production, use a proper min-cost flow library, but for now we'll use a greedy
    // approach that considers all pairs

    List<SettlementPlanResponse.TransferItem> transfers = new ArrayList<>();
    Map<Long, User> userCache = new HashMap<>();

    // Create lists of creditors and debtors
    List<BalanceEntry> creditors = new ArrayList<>();
    List<BalanceEntry> debtors = new ArrayList<>();

    for (Map.Entry<Long, BigDecimal> entry : netBalances.entrySet()) {
      BigDecimal balance = entry.getValue();
      User user = userCache.computeIfAbsent(entry.getKey(), userMapper::selectById);
      if (user == null) {
        continue;
      }

      if (balance.compareTo(BigDecimal.ZERO) > 0) {
        creditors.add(new BalanceEntry(entry.getKey(), user.getName(), balance));
      } else if (balance.compareTo(BigDecimal.ZERO) < 0) {
        debtors.add(new BalanceEntry(entry.getKey(), user.getName(), balance.abs()));
      }
    }

    // Sort by amount (largest first for creditors, largest first for debtors)
    creditors.sort((a, b) -> b.getAmount().compareTo(a.getAmount()));
    debtors.sort((a, b) -> b.getAmount().compareTo(a.getAmount()));

    // Greedy matching with constraint checking
    boolean[] creditorUsed = new boolean[creditors.size()];
    boolean[] debtorUsed = new boolean[debtors.size()];

    for (int i = 0; i < creditors.size(); i++) {
      if (creditorUsed[i]) {
        continue;
      }

      BalanceEntry creditor = creditors.get(i);

      for (int j = 0; j < debtors.size(); j++) {
        if (debtorUsed[j]) {
          continue;
        }

        BalanceEntry debtor = debtors.get(j);

        // Check payment channel constraint
        if (!isPaymentChannelAllowed(debtor.getUserId(), creditor.getUserId(), config)) {
          continue;
        }

        BigDecimal transferAmount = creditor.getAmount().min(debtor.getAmount());

        // Apply cap if configured
        if (config.getMaxTransferAmount() != null
            && transferAmount.compareTo(config.getMaxTransferAmount()) > 0) {
          transferAmount = config.getMaxTransferAmount();
        }

        // Apply rounding
        transferAmount = applyRounding(transferAmount, config, baseCurrency);

        if (transferAmount.compareTo(BigDecimal.ZERO) > 0) {
          SettlementPlanResponse.TransferItem transfer =
              new SettlementPlanResponse.TransferItem(
                  debtor.getUserId(),
                  debtor.getUserName(),
                  creditor.getUserId(),
                  creditor.getUserName(),
                  transferAmount);
          transfers.add(transfer);

          // Update balances
          BigDecimal remainingCreditor = creditor.getAmount().subtract(transferAmount);
          BigDecimal remainingDebtor = debtor.getAmount().subtract(transferAmount);

          if (remainingCreditor.compareTo(BigDecimal.ZERO) <= 0) {
            creditorUsed[i] = true;
          } else {
            creditors.set(
                i,
                new BalanceEntry(creditor.getUserId(), creditor.getUserName(), remainingCreditor));
          }

          if (remainingDebtor.compareTo(BigDecimal.ZERO) <= 0) {
            debtorUsed[j] = true;
            break;
          } else {
            debtors.set(
                j, new BalanceEntry(debtor.getUserId(), debtor.getUserName(), remainingDebtor));
          }
        }
      }
    }

    return transfers;
  }

  /**
   * Process settlements with constraints (caps, payment channels, rounding).
   *
   * @param creditors priority queue of creditors
   * @param debtors priority queue of debtors
   * @param transfers list to add transfers to
   * @param config settlement configuration
   * @param baseCurrency currency for rounding
   */
  private void processSettlementsWithConstraints(
      PriorityQueue<BalanceEntry> creditors,
      PriorityQueue<BalanceEntry> debtors,
      List<SettlementPlanResponse.TransferItem> transfers,
      SettlementConfig config,
      String baseCurrency) {
    // Use iterative approach to avoid infinite recursion with blocked channels
    // Higher limit to handle capped transfers that need multiple iterations
    int maxIterations = 1000; // Safety limit - high enough for capped transfers
    int iterations = 0;
    Set<String> triedPairs = new HashSet<>(); // Track tried pairs to avoid infinite loops

    while (!creditors.isEmpty() && !debtors.isEmpty() && iterations < maxIterations) {
      iterations++;

      BalanceEntry creditor = creditors.poll();
      BalanceEntry debtor = debtors.poll();

      // Create pair key to track tried combinations (for blocked channels)
      String pairKey = debtor.getUserId() + "-" + creditor.getUserId();

      // Check payment channel constraint
      if (!isPaymentChannelAllowed(debtor.getUserId(), creditor.getUserId(), config)) {
        triedPairs.add(pairKey);
        // If we've tried all possible pairs, break
        if (triedPairs.size() >= creditors.size() * debtors.size() * 2) {
          creditors.offer(creditor);
          debtors.offer(debtor);
          break;
        }
        // Try to find alternative pairing - put creditor back, try different debtor
        creditors.offer(creditor);
        // Put debtor at end to try other creditors first
        debtors.offer(debtor);
        continue;
      }

      BigDecimal transferAmount = creditor.getAmount().min(debtor.getAmount());

      // Apply cap if configured
      if (config.getMaxTransferAmount() != null
          && transferAmount.compareTo(config.getMaxTransferAmount()) > 0) {
        transferAmount = config.getMaxTransferAmount();
      }

      // Apply rounding
      transferAmount = applyRounding(transferAmount, config, baseCurrency);

      if (transferAmount.compareTo(BigDecimal.ZERO) > 0) {
        SettlementPlanResponse.TransferItem transfer =
            new SettlementPlanResponse.TransferItem(
                debtor.getUserId(),
                debtor.getUserName(),
                creditor.getUserId(),
                creditor.getUserName(),
                transferAmount);
        transfers.add(transfer);

        BigDecimal remainingCreditor = creditor.getAmount().subtract(transferAmount);
        BigDecimal remainingDebtor = debtor.getAmount().subtract(transferAmount);

        // Clear tried pairs when we successfully create a transfer
        // This allows the same pair to be processed again if capped
        triedPairs.clear();

        if (remainingCreditor.compareTo(BigDecimal.ZERO) > 0) {
          creditors.offer(
              new BalanceEntry(creditor.getUserId(), creditor.getUserName(), remainingCreditor));
        }

        if (remainingDebtor.compareTo(BigDecimal.ZERO) > 0) {
          debtors.offer(
              new BalanceEntry(debtor.getUserId(), debtor.getUserName(), remainingDebtor));
        }
      } else {
        // Put them back if transfer amount is zero (shouldn't happen, but safety check)
        creditors.offer(creditor);
        debtors.offer(debtor);
        break; // Avoid infinite loop
      }
    }
  }

  /**
   * Check if payment channel is allowed between two users.
   *
   * @param fromUserId payer user ID
   * @param toUserId receiver user ID
   * @param config settlement configuration
   * @return true if payment is allowed
   */
  private boolean isPaymentChannelAllowed(Long fromUserId, Long toUserId, SettlementConfig config) {
    if (config == null || config.getPaymentChannels() == null) {
      return true; // No constraints means all channels allowed
    }

    String channelKey = fromUserId + "-" + toUserId;
    Set<String> allowedChannels = config.getPaymentChannels().get(channelKey);

    // If no specific channels defined for this pair, allow
    if (allowedChannels == null) {
      return true;
    }

    // Empty set means blocked, non-empty set means allowed (with those channels)
    return !allowedChannels.isEmpty();
  }

  /**
   * Apply rounding to amount based on currency and rounding strategy.
   *
   * @param amount amount to round
   * @param config settlement configuration
   * @param currency currency code
   * @return rounded amount
   */
  private BigDecimal applyRounding(BigDecimal amount, SettlementConfig config, String currency) {
    // Get currency exponent
    Currency currencyEntity = currencyMapper.selectById(currency);
    int exponent = currencyEntity != null ? currencyEntity.getExponent() : 2;

    String strategy = "ROUND_HALF_UP"; // Default
    if (config != null && config.getRoundingStrategy() != null) {
      strategy = config.getRoundingStrategy();
    }

    switch (strategy) {
      case "ROUND_HALF_UP":
        return amount.setScale(exponent, RoundingMode.HALF_UP);
      case "TRIM_TO_UNIT":
        return amount.setScale(exponent, RoundingMode.DOWN);
      case "NONE":
        return amount;
      default:
        return amount.setScale(exponent, RoundingMode.HALF_UP);
    }
  }

  /** Helper class for balance entries used in priority queues. */
  private static class BalanceEntry {
    private final Long userId;
    private final String userName;
    private final BigDecimal amount;

    BalanceEntry(Long userId, String userName, BigDecimal amount) {
      this.userId = userId;
      this.userName = userName;
      this.amount = amount;
    }

    Long getUserId() {
      return userId;
    }

    String getUserName() {
      return userName;
    }

    BigDecimal getAmount() {
      return amount;
    }
  }

  /**
   * Create category from the request for a new ledger.
   *
   * @param ledgerId the ledger ID
   * @param categoryRequest the category request from the client
   */
  private void createCategoryFromRequest(Long ledgerId, CreateCategoryRequest categoryRequest) {
    Category category = new Category();
    category.setLedgerId(ledgerId);
    category.setName(categoryRequest.getName());
    category.setKind(categoryRequest.getKind());
    category.setIsActive(categoryRequest.getIsActive());

    // Auto-assign sortOrder to avoid conflicts
    Integer nextSortOrder = getNextSortOrderForLedger(ledgerId);
    category.setSortOrder(nextSortOrder);

    categoryMapper.insert(category);
  }

  /**
   * Get the next available sortOrder for a ledger (auto-increment by 10).
   *
   * @param ledgerId the ledger ID
   * @return the next sortOrder value
   */
  private Integer getNextSortOrderForLedger(Long ledgerId) {
    // Count existing categories in this ledger and assign next order
    Long categoryCount =
        categoryMapper.selectCount(
            new LambdaQueryWrapper<Category>().eq(Category::getLedgerId, ledgerId));

    // Return next value (increment by 10 to leave room for manual reordering)
    return (categoryCount.intValue() + 1) * 10;
  }
}
