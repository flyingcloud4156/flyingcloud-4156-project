package dev.coms4156.project.groupproject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.coms4156.project.groupproject.dto.AddLedgerMemberRequest;
import dev.coms4156.project.groupproject.dto.CreateLedgerRequest;
import dev.coms4156.project.groupproject.dto.LedgerMemberResponse;
import dev.coms4156.project.groupproject.dto.LedgerResponse;
import dev.coms4156.project.groupproject.dto.ListLedgerMembersResponse;
import dev.coms4156.project.groupproject.dto.MyLedgersResponse;
import dev.coms4156.project.groupproject.dto.NetBalanceResponse;
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

  @Override
  public NetBalanceResponse getNetBalance(Long ledgerId) {
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

    Map<String, BigDecimal> netMap = new HashMap<>();

    for (DebtEdge edge : allEdges) {
      String key = edge.getFromUserId() + "->" + edge.getToUserId();
      netMap.put(key, netMap.getOrDefault(key, BigDecimal.ZERO).add(edge.getAmount()));
    }

    List<NetBalanceResponse.NetBalanceItem> items = new ArrayList<>();

    Map<Long, User> userCache = new HashMap<>();

    Map<String, Boolean> processed = new HashMap<>();

    for (Map.Entry<String, BigDecimal> entry : netMap.entrySet()) {
      String[] parts = entry.getKey().split("->");
      Long fromUserId = Long.parseLong(parts[0]);
      Long toUserId = Long.parseLong(parts[1]);

      String reverseKey = toUserId + "->" + fromUserId;

      if (processed.containsKey(reverseKey) || processed.containsKey(entry.getKey())) {
        continue;
      }

      processed.put(entry.getKey(), true);
      processed.put(reverseKey, true);

      BigDecimal forwardAmount = entry.getValue();
      BigDecimal reverseAmount = netMap.getOrDefault(reverseKey, BigDecimal.ZERO);

      BigDecimal netAmount = forwardAmount.subtract(reverseAmount);

      if (netAmount.compareTo(BigDecimal.ZERO) > 0) {
        Long creditorId = fromUserId;
        Long debtorId = toUserId;

        User creditor = userCache.computeIfAbsent(creditorId, userMapper::selectById);
        User debtor = userCache.computeIfAbsent(debtorId, userMapper::selectById);

        if (creditor != null && debtor != null) {
          items.add(
              new NetBalanceResponse.NetBalanceItem(
                  creditorId, creditor.getName(), debtorId, debtor.getName(), netAmount));
        }
      } else if (netAmount.compareTo(BigDecimal.ZERO) < 0) {
        Long creditorId = toUserId;
        Long debtorId = fromUserId;
        BigDecimal positiveNet = netAmount.negate();

        User creditor = userCache.computeIfAbsent(creditorId, userMapper::selectById);
        User debtor = userCache.computeIfAbsent(debtorId, userMapper::selectById);

        if (creditor != null && debtor != null) {
          items.add(
              new NetBalanceResponse.NetBalanceItem(
                  creditorId, creditor.getName(), debtorId, debtor.getName(), positiveNet));
        }
      }
    }

    items.sort(
        (a, b) -> {
          int creditorCompare = a.getCreditorId().compareTo(b.getCreditorId());
          if (creditorCompare != 0) {
            return creditorCompare;
          }
          return a.getDebtorId().compareTo(b.getDebtorId());
        });

    return new NetBalanceResponse(ledgerId, ledger.getBaseCurrency(), items);
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
}
