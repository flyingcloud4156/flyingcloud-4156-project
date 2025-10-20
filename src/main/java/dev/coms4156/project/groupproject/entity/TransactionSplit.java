package dev.coms4156.project.groupproject.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/**
 * Transaction split entity mapping table 'transaction_splits'. Represents how a user participates
 * in a transaction split.
 */
@Data
@TableName("transaction_splits")
public class TransactionSplit {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long transactionId;
  private Long userId;
  private String splitMethod;
  private BigDecimal shareValue;
  private Boolean included;
  private BigDecimal computedAmount;
}
