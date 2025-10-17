package dev.coms4156.project.groupproject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyLedgersResponse {
    private List<LedgerItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerItem {
        private Long ledgerId;
        private String name;
        private String ledgerType;
        private String baseCurrency;
        private String role;
    }
}
