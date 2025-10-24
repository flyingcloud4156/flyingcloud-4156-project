package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Request to add a member to a ledger. */
@Data
public class AddLedgerMemberRequest {
  @NotNull
  @Schema(example = "2")
  private Long userId;

  @NotNull
  @Schema(example = "EDITOR")
  private String role;
}
