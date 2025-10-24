package dev.coms4156.project.groupproject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.coms4156.project.groupproject.dto.CreateTransactionRequest;
import dev.coms4156.project.groupproject.dto.CreateTransactionResponse;
import dev.coms4156.project.groupproject.dto.EdgePreview;
import dev.coms4156.project.groupproject.dto.ListTransactionsResponse;
import dev.coms4156.project.groupproject.dto.SplitItem;
import dev.coms4156.project.groupproject.dto.SplitView;
import dev.coms4156.project.groupproject.dto.TransactionResponse;
import dev.coms4156.project.groupproject.dto.TransactionSummary;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.Currency;
import dev.coms4156.project.groupproject.entity.DebtEdge;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.entity.Transaction;
import dev.coms4156.project.groupproject.entity.TransactionSplit;
import dev.coms4156.project.groupproject.mapper.CurrencyMapper;
import dev.coms4156.project.groupproject.mapper.DebtEdgeMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.TransactionMapper;
import dev.coms4156.project.groupproject.mapper.TransactionSplitMapper;
import dev.coms4156.project.groupproject.service.TransactionService;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of TransactionService. Handles transaction operations with split calculations and
 * debt edge generation.
 */
@Service
public class TransactionServiceImpl implements TransactionService {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final TransactionMapper transactionMapper;
  private final TransactionSplitMapper transactionSplitMapper;
  private final DebtEdgeMapper debtEdgeMapper;
  private final LedgerMapper ledgerMapper;
  private final LedgerMemberMapper ledgerMemberMapper;
  private final CurrencyMapper currencyMapper;

  /**
   * Constructor for TransactionServiceImpl.
   *
   * @param transactionMapper mapper for transaction operations
   * @param transactionSplitMapper mapper for transaction split operations
   * @param debtEdgeMapper mapper for debt edge operations
   * @param ledgerMapper mapper for ledger operations
   * @param ledgerMemberMapper mapper for ledger member operations
   * @param currencyMapper mapper for currency operations
   */
  @Autowired
  public TransactionServiceImpl(
      TransactionMapper transactionMapper,
      TransactionSplitMapper transactionSplitMapper,
      DebtEdgeMapper debtEdgeMapper,
      LedgerMapper ledgerMapper,
      LedgerMemberMapper ledgerMemberMapper,
      CurrencyMapper currencyMapper) {
    this.transactionMapper = transactionMapper;
    this.transactionSplitMapper = transactionSplitMapper;
    this.debtEdgeMapper = debtEdgeMapper;
    this.ledgerMapper = ledgerMapper;
    this.ledgerMemberMapper = ledgerMemberMapper;
    this.currencyMapper = currencyMapper;
  }

  @Override
  @Transactional
  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  public CreateTransactionResponse createTransaction(
      Long ledgerId, CreateTransactionRequest request) {
    UserView currentUser = CurrentUserContext.get();
    if (currentUser == null) {
      throw new RuntimeException("Not logged in");
    }

    // Validate ledger and membership
    Ledger ledger = validateLedgerAndMembership(ledgerId, currentUser.getId());

    // Validate currency matches ledger base currency
    if (!request.getCurrency().equals(ledger.getBaseCurrency())) {
      throw new RuntimeException("Currency mismatch");
    }

    // Create transaction entity
    Transaction transaction = new Transaction();
    transaction.setLedgerId(ledgerId);
    transaction.setCreatedBy(currentUser.getId());
    transaction.setTxnAt(request.getTxnAt());
    transaction.setType(request.getType());
    transaction.setPayerId(request.getPayerId());
    transaction.setAmountTotal(request.getAmountTotal());
    transaction.setCurrency(request.getCurrency());
    transaction.setNote(request.getNote());
    transaction.setIsPrivate(request.getIsPrivate());
    transaction.setRoundingStrategy(request.getRoundingStrategy());
    transaction.setTailAllocation(request.getTailAllocation());
    transaction.setCreatedAt(LocalDateTime.now());
    transaction.setUpdatedAt(LocalDateTime.now());

    // Insert transaction
    transactionMapper.insert(transaction);
    Long transactionId = transaction.getId();

    // Handle EXPENSE/INCOME with splits
    handleSplitTransaction(ledgerId, transactionId, request, ledger);

    CreateTransactionResponse response = new CreateTransactionResponse();
    response.setTransactionId(transactionId);
    return response;
  }

  @Override
  public TransactionResponse getTransaction(Long ledgerId, Long transactionId) {
    UserView currentUser = CurrentUserContext.get();
    if (currentUser == null) {
      throw new RuntimeException("Not logged in");
    }

    // Validate ledger membership
    validateLedgerAndMembership(ledgerId, currentUser.getId());

    // Get transaction with visibility check
    Transaction transaction =
        transactionMapper.findTransactionByIdWithVisibility(transactionId, currentUser.getId());
    if (transaction == null) {
      throw new RuntimeException("Transaction not found");
    }

    if (!transaction.getLedgerId().equals(ledgerId)) {
      throw new RuntimeException("Transaction not found in this ledger");
    }

    return buildTransactionResponse(transaction);
  }

  @Override
  public ListTransactionsResponse listTransactions(
      Long ledgerId,
      Integer page,
      Integer size,
      String fromDate,
      String toDate,
      String type,
      Long createdBy) {

    UserView currentUser = CurrentUserContext.get();
    if (currentUser == null) {
      throw new RuntimeException("Not logged in");
    }

    // Validate ledger membership
    validateLedgerAndMembership(ledgerId, currentUser.getId());

    // Parse dates
    LocalDateTime from = null;
    LocalDateTime to = null;
    if (fromDate != null) {
      from = LocalDateTime.parse(fromDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    if (toDate != null) {
      to = LocalDateTime.parse(toDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    // Create pagination
    Page<Transaction> pageObj = new Page<>(page, size);

    // Query transactions
    IPage<Transaction> result =
        transactionMapper.findTransactionsByLedger(
            pageObj, ledgerId, from, to, type, createdBy, null, currentUser.getId());

    // Convert to response
    List<TransactionSummary> items =
        result.getRecords().stream()
            .map(this::buildTransactionSummary)
            .collect(Collectors.toList());

    ListTransactionsResponse response = new ListTransactionsResponse();
    response.setPage(page);
    response.setSize(size);
    response.setTotal(result.getTotal());
    response.setItems(items);

    return response;
  }

  @Override
  @Transactional
  public void deleteTransaction(Long ledgerId, Long transactionId) {
    UserView currentUser = CurrentUserContext.get();
    if (currentUser == null) {
      throw new RuntimeException("Not logged in");
    }

    // Validate ledger membership
    validateLedgerAndMembership(ledgerId, currentUser.getId());

    // Get transaction
    Transaction transaction =
        transactionMapper.findTransactionByIdWithVisibility(transactionId, currentUser.getId());
    if (transaction == null) {
      throw new RuntimeException("Transaction not found");
    }

    if (!transaction.getLedgerId().equals(ledgerId)) {
      throw new RuntimeException("Transaction not found in this ledger");
    }

    // TODO: Re-implement permission check for deletion if needed

    // Delete in order: debt edges, splits, transaction
    debtEdgeMapper.deleteByTransactionId(transactionId);
    transactionSplitMapper.deleteByTransactionId(transactionId);
    transactionMapper.deleteById(transactionId);
  }

  // Private helper methods

  private Ledger validateLedgerAndMembership(Long ledgerId, Long userId) {
    Ledger ledger = ledgerMapper.selectById(ledgerId);
    if (ledger == null) {
      throw new RuntimeException("Ledger not found");
    }

    // Check membership
    LambdaQueryWrapper<LedgerMember> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(LedgerMember::getLedgerId, ledgerId).eq(LedgerMember::getUserId, userId);

    LedgerMember member = ledgerMemberMapper.selectOne(wrapper);
    if (member == null) {
      throw new RuntimeException("User not a member of this ledger");
    }

    return ledger;
  }

  private void handleSplitTransaction(
      Long ledgerId, Long transactionId, CreateTransactionRequest request, Ledger ledger) {
    if (request.getSplits() == null || request.getSplits().isEmpty()) {
      throw new RuntimeException("Splits required for EXPENSE/INCOME type");
    }

    // Pre-validation: Ensure all users in the split are members of the ledger
    List<Long> memberIds =
        ledgerMemberMapper
            .selectList(
                new LambdaQueryWrapper<LedgerMember>().eq(LedgerMember::getLedgerId, ledgerId))
            .stream()
            .map(LedgerMember::getUserId)
            .collect(Collectors.toList());
    List<Long> splitUserIds =
        request.getSplits().stream().map(SplitItem::getUserId).collect(Collectors.toList());
    if (!memberIds.containsAll(splitUserIds)) {
      throw new RuntimeException("One or more users in the split are not members of the ledger.");
    }

    // Validate splits
    validateSplits(request.getSplits(), request.getAmountTotal());

    // Calculate split amounts
    Map<Long, BigDecimal> computedAmounts = calculateSplitAmounts(request);

    // Create transaction splits
    List<TransactionSplit> splits = new ArrayList<>();
    for (SplitItem item : request.getSplits()) {
      TransactionSplit split = new TransactionSplit(); // NOPMD - Must create new object in loop
      split.setTransactionId(transactionId);
      split.setUserId(item.getUserId());
      split.setSplitMethod(item.getSplitMethod());
      split.setShareValue(item.getShareValue());
      split.setIncluded(item.getIncluded());
      split.setComputedAmount(computedAmounts.get(item.getUserId()));
      splits.add(split);
    }

    transactionSplitMapper.insertBatch(splits);

    // Generate debt edges for GROUP_BALANCE ledgers
    if ("GROUP_BALANCE".equals(ledger.getLedgerType())) {
      generateDebtEdges(ledgerId, transactionId, request, computedAmounts);
    }
  }

  private void validateSplits(List<SplitItem> splits, BigDecimal amountTotal) {
    // Check for duplicate user IDs
    long uniqueUserIds = splits.stream().map(SplitItem::getUserId).distinct().count();
    if (uniqueUserIds != splits.size()) {
      throw new RuntimeException("Duplicate user IDs in splits");
    }

    // Check at least one user is included
    long includedCount =
        splits.stream().filter(split -> Boolean.TRUE.equals(split.getIncluded())).count();
    if (includedCount == 0) {
      throw new RuntimeException("At least one user must be included");
    }

    // Validate split method specific constraints
    for (SplitItem split : splits) {
      if (Boolean.TRUE.equals(split.getIncluded())) {
        validateSplitMethod(split);
      }
    }

    // Validate total for EXACT method
    String exactMethod =
        splits.stream()
            .filter(
                split ->
                    "EXACT".equals(split.getSplitMethod())
                        && Boolean.TRUE.equals(split.getIncluded()))
            .map(SplitItem::getSplitMethod)
            .findFirst()
            .orElse(null);

    if (exactMethod != null) {
      BigDecimal exactTotal =
          splits.stream()
              .filter(split -> Boolean.TRUE.equals(split.getIncluded()))
              .map(SplitItem::getShareValue)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      if (exactTotal.compareTo(amountTotal) != 0) {
        throw new RuntimeException("EXACT splits must sum to total amount");
      }
    }
  }

  private void validateSplitMethod(SplitItem split) {
    switch (split.getSplitMethod()) {
      case "PERCENT":
        if (split.getShareValue().compareTo(BigDecimal.ZERO) < 0
            || split.getShareValue().compareTo(HUNDRED) > 0) {
          throw new RuntimeException("PERCENT share value must be between 0 and 100");
        }
        break;
      case "WEIGHT":
        if (split.getShareValue().compareTo(BigDecimal.ZERO) <= 0) {
          throw new RuntimeException("WEIGHT share value must be positive");
        }
        break;
      case "EXACT":
        if (split.getShareValue().compareTo(BigDecimal.ZERO) < 0) {
          throw new RuntimeException("EXACT share value must be non-negative");
        }
        break;
      case "EQUAL":
        // No validation needed for EQUAL
        break;
      default:
        throw new RuntimeException("Invalid split method: " + split.getSplitMethod());
    }
  }

  private Map<Long, BigDecimal> calculateSplitAmounts(CreateTransactionRequest request) {
    List<SplitItem> includedSplits =
        request.getSplits().stream()
            .filter(split -> Boolean.TRUE.equals(split.getIncluded()))
            .collect(Collectors.toList());

    BigDecimal totalAmount = request.getAmountTotal();
    Map<Long, BigDecimal> rawShares = new HashMap<>();

    // Calculate raw shares based on split method
    switch (includedSplits.get(0).getSplitMethod()) {
      case "EQUAL":
        BigDecimal equalAmount =
            totalAmount.divide(new BigDecimal(includedSplits.size()), 8, RoundingMode.HALF_UP);
        for (SplitItem split : includedSplits) {
          rawShares.put(split.getUserId(), equalAmount);
        }
        break;

      case "PERCENT":
        // Validate total percentage
        BigDecimal totalPercent =
            includedSplits.stream()
                .map(SplitItem::getShareValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalPercent.compareTo(HUNDRED) != 0) {
          throw new RuntimeException("PERCENT splits must sum to 100");
        }

        for (SplitItem split : includedSplits) {
          BigDecimal amount =
              totalAmount.multiply(split.getShareValue()).divide(HUNDRED, 8, RoundingMode.HALF_UP);
          rawShares.put(split.getUserId(), amount);
        }
        break;

      case "WEIGHT":
        BigDecimal totalWeight =
            includedSplits.stream()
                .map(SplitItem::getShareValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (SplitItem split : includedSplits) {
          BigDecimal amount =
              totalAmount
                  .multiply(split.getShareValue())
                  .divide(totalWeight, 8, RoundingMode.HALF_UP);
          rawShares.put(split.getUserId(), amount);
        }
        break;

      case "EXACT":
        for (SplitItem split : includedSplits) {
          rawShares.put(split.getUserId(), split.getShareValue());
        }
        break;

      default:
        throw new RuntimeException("Invalid split method");
    }

    // Apply rounding and tail allocation
    return applyRoundingAndTailAllocation(rawShares, totalAmount, request);
  }

  private Map<Long, BigDecimal> applyRoundingAndTailAllocation(
      Map<Long, BigDecimal> rawShares, BigDecimal totalAmount, CreateTransactionRequest request) {

    // Get currency exponent for rounding
    Currency currency = currencyMapper.selectById(request.getCurrency());
    int exponent = currency != null ? currency.getExponent() : 2;

    Map<Long, BigDecimal> rounded = new HashMap<>();
    BigDecimal roundedSum = BigDecimal.ZERO;

    // Apply local rounding
    for (Map.Entry<Long, BigDecimal> entry : rawShares.entrySet()) {
      BigDecimal roundedAmount;
      switch (request.getRoundingStrategy()) {
        case "ROUND_HALF_UP":
          roundedAmount = entry.getValue().setScale(exponent, RoundingMode.HALF_UP);
          break;
        case "TRIM_TO_UNIT":
          roundedAmount = entry.getValue().setScale(exponent, RoundingMode.DOWN);
          break;
        case "NONE":
          roundedAmount = entry.getValue();
          break;
        default:
          roundedAmount = entry.getValue().setScale(exponent, RoundingMode.HALF_UP);
      }
      rounded.put(entry.getKey(), roundedAmount);
      roundedSum = roundedSum.add(roundedAmount);
    }

    // Calculate tail
    BigDecimal tail = totalAmount.subtract(roundedSum);

    // Allocate tail
    if (tail.compareTo(BigDecimal.ZERO) != 0) {
      Long targetUserId = determineTailTarget(request, rounded);
      if (targetUserId != null) {
        BigDecimal currentAmount = rounded.get(targetUserId);
        rounded.put(targetUserId, currentAmount.add(tail));
      }
    }

    return rounded;
  }

  private Long determineTailTarget(
      CreateTransactionRequest request, Map<Long, BigDecimal> rounded) {
    switch (request.getTailAllocation()) {
      case "PAYER":
        return request.getPayerId();
      case "LARGEST_SHARE":
        return rounded.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
      case "CREATOR":
        UserView currentUser = CurrentUserContext.get();
        return currentUser != null ? currentUser.getId() : null;
      default:
        return request.getPayerId();
    }
  }

  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  private void generateDebtEdges(
      Long ledgerId,
      Long transactionId,
      CreateTransactionRequest request,
      Map<Long, BigDecimal> computedAmounts) {

    List<DebtEdge> edges = new ArrayList<>();

    if ("EXPENSE".equals(request.getType())) {
      // For EXPENSE: payer -> participants (participants owe payer)
      for (Map.Entry<Long, BigDecimal> entry : computedAmounts.entrySet()) {
        if (!entry.getKey().equals(request.getPayerId())) {
          DebtEdge edge = new DebtEdge(); // NOPMD - Must create new object in loop
          edge.setLedgerId(ledgerId);
          edge.setTransactionId(transactionId);
          edge.setFromUserId(request.getPayerId()); // Creditor
          edge.setToUserId(entry.getKey()); // Debtor
          edge.setAmount(entry.getValue());
          edge.setEdgeCurrency(request.getCurrency());
          edge.setCreatedAt(LocalDateTime.now());
          edges.add(edge);
        }
      }
    } else if ("INCOME".equals(request.getType())) {
      // For INCOME: participants -> payer (payer owes participants)
      for (Map.Entry<Long, BigDecimal> entry : computedAmounts.entrySet()) {
        if (!entry.getKey().equals(request.getPayerId())) {
          DebtEdge edge = new DebtEdge(); // NOPMD - Must create new object in loop
          edge.setLedgerId(ledgerId);
          edge.setTransactionId(transactionId);
          edge.setFromUserId(entry.getKey()); // Creditor
          edge.setToUserId(request.getPayerId()); // Debtor
          edge.setAmount(entry.getValue());
          edge.setEdgeCurrency(request.getCurrency());
          edge.setCreatedAt(LocalDateTime.now());
          edges.add(edge);
        }
      }
    }

    if (!edges.isEmpty()) {
      debtEdgeMapper.insertBatch(edges);
    }
  }

  private TransactionResponse buildTransactionResponse(Transaction transaction) {
    TransactionResponse response = new TransactionResponse();
    response.setTransactionId(transaction.getId());
    response.setLedgerId(transaction.getLedgerId());
    response.setTxnAt(transaction.getTxnAt());
    response.setType(transaction.getType());
    response.setCurrency(transaction.getCurrency());
    response.setAmountTotal(transaction.getAmountTotal());
    response.setNote(transaction.getNote());
    response.setPayerId(transaction.getPayerId());
    response.setCreatedBy(transaction.getCreatedBy());
    response.setRoundingStrategy(transaction.getRoundingStrategy());
    response.setTailAllocation(transaction.getTailAllocation());

    // Get splits
    List<TransactionSplit> splits = transactionSplitMapper.findByTransactionId(transaction.getId());
    List<SplitView> splitViews =
        splits.stream().map(this::buildSplitView).collect(Collectors.toList());
    response.setSplits(splitViews);

    // Get debt edges preview
    List<DebtEdge> edges = debtEdgeMapper.findByTransactionId(transaction.getId());
    List<EdgePreview> edgePreviews =
        edges.stream().map(this::buildEdgePreview).collect(Collectors.toList());
    response.setEdgesPreview(edgePreviews);

    return response;
  }

  private SplitView buildSplitView(TransactionSplit split) {
    SplitView view = new SplitView();
    view.setUserId(split.getUserId());
    view.setSplitMethod(split.getSplitMethod());
    view.setShareValue(split.getShareValue());
    view.setIncluded(split.getIncluded());
    view.setComputedAmount(split.getComputedAmount());
    return view;
  }

  private EdgePreview buildEdgePreview(DebtEdge edge) {
    EdgePreview preview = new EdgePreview();
    preview.setFromUserId(edge.getFromUserId());
    preview.setToUserId(edge.getToUserId());
    preview.setAmount(edge.getAmount());
    preview.setEdgeCurrency(edge.getEdgeCurrency());
    return preview;
  }

  private TransactionSummary buildTransactionSummary(Transaction transaction) {
    TransactionSummary summary = new TransactionSummary();
    summary.setTransactionId(transaction.getId());
    summary.setTxnAt(transaction.getTxnAt());
    summary.setType(transaction.getType());
    summary.setCurrency(transaction.getCurrency());
    summary.setAmountTotal(transaction.getAmountTotal());
    summary.setPayerId(transaction.getPayerId());
    summary.setCreatedBy(transaction.getCreatedBy());
    summary.setNote(transaction.getNote());
    return summary;
  }
}
