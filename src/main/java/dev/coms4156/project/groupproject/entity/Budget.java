package dev.coms4156.project.groupproject.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Budget entity mapping table 'budgets'. Represents a monthly budget for a ledger or specific
 * category.
 */
@Data
@TableName("budgets")
public class Budget {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long ledgerId;

  /** NULL means whole-ledger budget; non-NULL means category-specific budget. */
  private Long categoryId;

  private Integer year;
  private Integer month;
  private BigDecimal limitAmount;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
