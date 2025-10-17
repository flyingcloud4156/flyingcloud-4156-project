package dev.coms4156.project.groupproject.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Represents a ledger member. */
@Data
@TableName("ledger_members")
public class LedgerMember {
  private Long ledgerId;
  private Long userId;
  private String role;
  private LocalDateTime joinedAt;
}
