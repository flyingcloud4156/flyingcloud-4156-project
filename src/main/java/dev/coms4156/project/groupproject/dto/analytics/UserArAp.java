package dev.coms4156.project.groupproject.dto.analytics;

import java.math.BigDecimal;
import lombok.Data;

/** User accounts receivable and accounts payable. */
@Data
public class UserArAp {
  private Long userId;
  private String userName;
  private BigDecimal ar;
  private BigDecimal ap;
}
