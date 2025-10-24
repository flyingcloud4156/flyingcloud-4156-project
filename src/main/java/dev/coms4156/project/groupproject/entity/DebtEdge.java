package dev.coms4156.project.groupproject.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Debt edge entity mapping table 'debt_edges'. Represents a directed debt relationship:
 * from_user_id owes to_user_id.
 */
@Data
@TableName("debt_edges")
public class DebtEdge {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long ledgerId;
  private Long transactionId;
  private Long fromUserId; // Creditor (who is owed money)
  private Long toUserId; // Debtor (who owes money)
  private BigDecimal amount;
  private String edgeCurrency;
  private LocalDateTime createdAt;
}
