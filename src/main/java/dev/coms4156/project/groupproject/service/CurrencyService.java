package dev.coms4156.project.groupproject.service;

import com.baomidou.mybatisplus.extension.service.IService;
import dev.coms4156.project.groupproject.dto.CurrencyResponse;
import dev.coms4156.project.groupproject.entity.Currency;

/** Service for currency-related operations. */
public interface CurrencyService extends IService<Currency> {
  CurrencyResponse getAllCurrencies();
}
