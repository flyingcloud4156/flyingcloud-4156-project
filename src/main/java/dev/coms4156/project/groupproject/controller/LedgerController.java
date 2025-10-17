package dev.coms4156.project.groupproject.controller;

import dev.coms4156.project.groupproject.dto.*;
import dev.coms4156.project.groupproject.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ledgers")
@Tag(name = "Ledger APIs")
@SecurityRequirement(name = "X-Auth-Token")
public class LedgerController {

  private final LedgerService ledgerService;

  @Autowired
  public LedgerController(LedgerService ledgerService) {
    this.ledgerService = ledgerService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new ledger")
  public Result<LedgerResponse> createLedger(@Valid @RequestBody CreateLedgerRequest req) {
    return Result.ok(ledgerService.createLedger(req));
  }

  @GetMapping(":mine")
  @Operation(summary = "Get my ledgers")
  public Result<MyLedgersResponse> getMyLedgers() {
    return Result.ok(ledgerService.getMyLedgers());
  }

  @GetMapping("/{ledgerId}")
  @Operation(summary = "Get ledger details")
  public Result<LedgerResponse> getLedgerDetails(@PathVariable Long ledgerId) {
    return Result.ok(ledgerService.getLedgerDetails(ledgerId));
  }

  @PostMapping("/{ledgerId}/members")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add a member to a ledger")
  public Result<LedgerMemberResponse> addMember(
      @PathVariable Long ledgerId, @Valid @RequestBody AddLedgerMemberRequest req) {
    return Result.ok(ledgerService.addMember(ledgerId, req));
  }

  @GetMapping("/{ledgerId}/members")
  @Operation(summary = "List ledger members")
  public Result<ListLedgerMembersResponse> listMembers(@PathVariable Long ledgerId) {
    return Result.ok(ledgerService.listMembers(ledgerId));
  }

  @DeleteMapping("/{ledgerId}/members/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove a member from a ledger")
  public Result<Void> removeMember(@PathVariable Long ledgerId, @PathVariable Long userId) {
    ledgerService.removeMember(ledgerId, userId);
    return Result.ok();
  }
}
