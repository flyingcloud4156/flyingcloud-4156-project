package dev.coms4156.project.groupproject.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entity representing a category in the ledger system. Categories are used to group transactions
 * and organize budget allocations.
 */
@Data
@TableName("categories")
public class Category {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long ledgerId;
  private String name;
  private String kind;
  private Boolean isActive;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
