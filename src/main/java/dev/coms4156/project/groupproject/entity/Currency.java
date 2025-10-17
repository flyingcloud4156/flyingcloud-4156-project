package dev.coms4156.project.groupproject.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** Represents a currency. */
@Data
@TableName("currency")
public class Currency {
  private String code;
  private Integer exponent;
}
