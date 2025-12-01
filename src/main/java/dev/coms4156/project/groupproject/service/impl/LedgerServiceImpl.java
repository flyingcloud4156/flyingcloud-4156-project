package dev.coms4156.project.groupproject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.coms4156.project.groupproject.dto.AddLedgerMemberRequest;
import dev.coms4156.project.groupproject.dto.CreateLedgerRequest;
import dev.coms4156.project.groupproject.dto.LedgerMemberResponse;
import dev.coms4156.project.groupproject.dto.LedgerResponse;
import dev.coms4156.project.groupproject.dto.ListLedgerMembersResponse;
import dev.coms4156.project.groupproject.dto.MyLedgersResponse;
import dev.coms4156.project.groupproject.dto.SettlementPlanResponse;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.DebtEdge;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.mapper.DebtEdgeMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import dev.coms4156.project.groupproject.service.LedgerService;
import dev.coms4156.project.groupproject.utils.AuthUtils;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
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

  /**
   * Constructor for LedgerServiceImpl.
   *
   * @param ledgerMemberMapper ledger member mapper
   * @param userMapper user mapper
   * @param debtEdgeMapper debt edge mapper
   */
  @Autowired
  public LedgerServiceImpl(
      LedgerMemberMapper ledgerMemberMapper, UserMapper userMapper, DebtEdgeMapper debtEdgeMapper) {
    this.ledgerMemberMapper = ledgerMemberMapper;
    this.userMapper = userMapper;
    this.debtEdgeMapper = debtEdgeMapper;
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
    UserView currentUser = CurrentUserContext.get();
    if (currentUser == null) {
      throw new RuntimeException("AUTH_REQUIRED");
    }

    Ledger ledger = getById(ledgerId);
    if (ledger == null) {
      throw new RuntimeException("LEDGER_NOT_FOUND");
    }

    AuthUtils.checkMembership(isMember(ledgerId, currentUser.getId()));

    List<DebtEdge> allEdges = debtEdgeMapper.findByLedgerId(ledgerId);

    Map<Long, BigDecimal> netBalances = calculateNetBalances(allEdges);

    List<SettlementPlanResponse.TransferItem> transfers =
        generateMinimalSettlementPlan(netBalances);

    return new SettlementPlanResponse(
        ledgerId, ledger.getBaseCurrency(), transfers.size(), transfers);
  }

  /**
   * Calculate net balances from debt edges, handling symmetry (b requirement). If A owes B 20 and B
   * owes A 40, the net becomes B owes A 20.
   *
   * @param edges all debt edges for the ledger
   * @return map of user ID to net balance (positive = creditor, negative = debtor)
   */
  private Map<Long, BigDecimal> calculateNetBalances(List<DebtEdge> edges) {
    Map<Long, BigDecimal> balances = new HashMap<>();

    for (DebtEdge edge : edges) {
      Long creditorId = edge.getFromUserId();
      Long debtorId = edge.getToUserId();
      BigDecimal amount = edge.getAmount();

      balances.put(creditorId, balances.getOrDefault(creditorId, BigDecimal.ZERO).add(amount));
      balances.put(debtorId, balances.getOrDefault(debtorId, BigDecimal.ZERO).subtract(amount));
    }

    return balances;
  }

  /**
   * Generate minimal settlement plan using heap-greedy algorithm (a requirement). Matches largest
   * creditors with largest debtors to minimize number of transfers.
   *
   * @param netBalances map of user ID to net balance
   * @return list of transfer instructions (who pays whom how much)
   */
  private List<SettlementPlanResponse.TransferItem> generateMinimalSettlementPlan(
      Map<Long, BigDecimal> netBalances) {

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

    processSettlements(creditors, debtors, transfers);
    return transfers;
  }

  private void processSettlements(
      PriorityQueue<BalanceEntry> creditors,
      PriorityQueue<BalanceEntry> debtors,
      List<SettlementPlanResponse.TransferItem> transfers) {
    if (creditors.isEmpty() || debtors.isEmpty()) {
      return;
    }

    BalanceEntry creditor = creditors.poll();
    BalanceEntry debtor = debtors.poll();

    BigDecimal transferAmount = creditor.getAmount().min(debtor.getAmount());

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

    if (remainingCreditor.compareTo(BigDecimal.ZERO) > 0) {
      creditors.offer(
          new BalanceEntry(creditor.getUserId(), creditor.getUserName(), remainingCreditor));
    }

    if (remainingDebtor.compareTo(BigDecimal.ZERO) > 0) {
      debtors.offer(new BalanceEntry(debtor.getUserId(), debtor.getUserName(), remainingDebtor));
    }

    processSettlements(creditors, debtors, transfers);
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
}
