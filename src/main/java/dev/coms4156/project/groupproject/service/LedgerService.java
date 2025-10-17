package dev.coms4156.project.groupproject.service;

import com.baomidou.mybatisplus.extension.service.IService;
import dev.coms4156.project.groupproject.dto.*;
import dev.coms4156.project.groupproject.entity.Ledger;

public interface LedgerService extends IService<Ledger> {
    LedgerResponse createLedger(CreateLedgerRequest req);

    MyLedgersResponse getMyLedgers();

    LedgerResponse getLedgerDetails(Long ledgerId);

    LedgerMemberResponse addMember(Long ledgerId, AddLedgerMemberRequest req);

    ListLedgerMembersResponse listMembers(Long ledgerId);

    void removeMember(Long ledgerId, Long userId);
}
