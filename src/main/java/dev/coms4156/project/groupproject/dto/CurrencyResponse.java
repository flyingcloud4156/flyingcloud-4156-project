package dev.coms4156.project.groupproject.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyResponse {
  private List<CurrencyItem> items;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CurrencyItem {
    private String code;
    private Integer exponent;
  }
}
