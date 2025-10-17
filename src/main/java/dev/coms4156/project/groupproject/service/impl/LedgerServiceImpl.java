package dev.coms4156.project.groupproject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.coms4156.project.groupproject.dto.*;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.mapper.LedgerMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import dev.coms4156.project.groupproject.service.LedgerService;
import dev.coms4156.project.groupproject.utils.AuthUtils;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerServiceImpl extends ServiceImpl<LedgerMapper, Ledger> implements LedgerService {

  private final LedgerMemberMapper ledgerMemberMapper;
  private final UserMapper userMapper;

  @Autowired
  public LedgerServiceImpl(LedgerMemberMapper ledgerMemberMapper, UserMapper userMapper) {
    this.ledgerMemberMapper = ledgerMemberMapper;
    this.userMapper = userMapper;
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
    AuthUtils.B(member != null);

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
    AuthUtils.A(callingUserMember, "OWNER", "ADMIN");

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

    AuthUtils.B(isMember(ledgerId, currentUser.getId()));

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
    AuthUtils.A(callingUserMember, "OWNER", "ADMIN");

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
}
