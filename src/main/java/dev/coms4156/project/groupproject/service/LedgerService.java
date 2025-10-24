package dev.coms4156.project.groupproject.service;

import com.baomidou.mybatisplus.extension.service.IService;
import dev.coms4156.project.groupproject.dto.AddLedgerMemberRequest;
import dev.coms4156.project.groupproject.dto.CreateLedgerRequest;
import dev.coms4156.project.groupproject.dto.LedgerMemberResponse;
import dev.coms4156.project.groupproject.dto.LedgerResponse;
import dev.coms4156.project.groupproject.dto.ListLedgerMembersResponse;
import dev.coms4156.project.groupproject.dto.MyLedgersResponse;
import dev.coms4156.project.groupproject.entity.Ledger;

/** Service for ledger-related operations. */
public interface LedgerService extends IService<Ledger> {
  LedgerResponse createLedger(CreateLedgerRequest req);

  MyLedgersResponse getMyLedgers();

  LedgerResponse getLedgerDetails(Long ledgerId);

  LedgerMemberResponse addMember(Long ledgerId, AddLedgerMemberRequest req);

  ListLedgerMembersResponse listMembers(Long ledgerId);

  void removeMember(Long ledgerId, Long userId);
}
