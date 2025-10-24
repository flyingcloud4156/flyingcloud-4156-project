package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Response for creating a new transaction. */
@Data
@Schema(description = "Response for transaction creation")
public class CreateTransactionResponse {

  @Schema(description = "Created transaction ID", example = "1001")
  private Long transactionId;
}
