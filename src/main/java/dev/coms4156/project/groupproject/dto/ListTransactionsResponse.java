package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** Response for listing transactions with pagination. */
@Data
@Schema(description = "Paginated list of transactions")
public class ListTransactionsResponse {

  @Schema(description = "Current page number (1-based)", example = "1")
  private Integer page;

  @Schema(description = "Page size", example = "50")
  private Integer size;

  @Schema(description = "Total number of transactions", example = "123")
  private Long total;

  @Schema(description = "List of transaction summaries")
  private List<TransactionSummary> items;
}
