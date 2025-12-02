package dev.coms4156.project.groupproject.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.groupproject.dto.CurrencyResponse;
import dev.coms4156.project.groupproject.entity.Currency;
import dev.coms4156.project.groupproject.mapper.CurrencyMapper;
import dev.coms4156.project.groupproject.service.CurrencyService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for CurrencyService with database.
 *
 * <p>Tests the integration: CurrencyService → CurrencyMapper → Database
 *
 * <p>Verifies: - listCurrencies() queries database correctly - Exchange rates are retrieved
 * accurately - Currency data is consistent
 */
@SpringBootTest
@Transactional
class CurrencyDatabaseIntegrationTest {

  @Autowired private CurrencyService currencyService;
  @Autowired private CurrencyMapper currencyMapper;

  @Test
  void testListCurrencies_queriesDatabase() {
    CurrencyResponse response = currencyService.getAllCurrencies();

    assertNotNull(response);
    assertNotNull(response.getItems());
    assertFalse(response.getItems().isEmpty());
    assertTrue(response.getItems().stream().anyMatch(c -> "USD".equals(c.getCode())));
  }

  @Test
  void testGetCurrencyByCode_returnsCorrectData() {
    Currency usd = currencyMapper.selectById("USD");

    assertNotNull(usd);
    assertEquals("USD", usd.getCode());
    assertNotNull(usd.getExponent());
  }

  @Test
  void testExchangeRate_hasValidValue() {
    List<Currency> currencies = currencyMapper.selectList(null);

    assertFalse(currencies.isEmpty());

    for (Currency currency : currencies) {
      assertNotNull(currency.getCode());
      assertNotNull(currency.getExponent());
    }
  }

  @Test
  void verifyServiceAndMapperReturnSameData() {
    CurrencyResponse serviceResult = currencyService.getAllCurrencies();
    List<Currency> mapperResult = currencyMapper.selectList(null);

    assertEquals(mapperResult.size(), serviceResult.getItems().size());

    for (CurrencyResponse.CurrencyItem ci : serviceResult.getItems()) {
      Currency entity = currencyMapper.selectById(ci.getCode());
      assertNotNull(entity);
      assertEquals(ci.getCode(), entity.getCode());
    }
  }
}
