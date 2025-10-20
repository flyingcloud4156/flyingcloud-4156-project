package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Response for updating a transaction. */
@Data
@Schema(description = "Response for transaction update")
public class UpdateTransactionResponse {

  @Schema(description = "Updated transaction ID", example = "1001")
  private Long transactionId;

  @Schema(description = "Whether the transaction was updated", example = "true")
  private Boolean updated;
}
