package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Request DTO for adding a member to a ledger. */
@Data
@Schema(description = "Request to add a member to a ledger")
public class AddLedgerMemberRequest {

  @NotNull(message = "User ID is required")
  @Schema(description = "User ID to add", example = "222", required = true)
  private Long userId;

  @NotNull(message = "Role is required")
  @Pattern(regexp = "ADMIN|EDITOR|VIEWER", message = "Role must be ADMIN, EDITOR, or VIEWER")
  @Schema(
      description = "Role of the new member",
      example = "EDITOR",
      allowableValues = {"ADMIN", "EDITOR", "VIEWER"},
      required = true)
  private String role;
}
