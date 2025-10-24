package dev.coms4156.project.groupproject.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for listing supported currencies. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyResponse {
  private List<CurrencyItem> items;

  /** Represents a single currency item. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CurrencyItem {
    private String code;
    private Integer exponent;
  }
}
