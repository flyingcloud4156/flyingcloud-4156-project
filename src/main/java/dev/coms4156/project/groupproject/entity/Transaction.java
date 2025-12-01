package dev.coms4156.project.groupproject.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Transaction entity mapping table 'transactions'. Represents a financial transaction with splits
 * and debt edge generation.
 */
@Data
@TableName("transactions")
public class Transaction {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long ledgerId;
  private Long createdBy;
  private LocalDateTime txnAt;
  private String type;
  private Long categoryId;
  private Long payerId;
  private BigDecimal amountTotal;
  private String currency;
  private String note;
  private Boolean isPrivate;
  private String roundingStrategy;
  private String tailAllocation;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
