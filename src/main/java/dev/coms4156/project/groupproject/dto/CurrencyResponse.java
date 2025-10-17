package dev.coms4156.project.groupproject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
