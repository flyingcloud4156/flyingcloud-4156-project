package dev.coms4156.project.groupproject.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.coms4156.project.groupproject.dto.CurrencyResponse;
import dev.coms4156.project.groupproject.entity.Currency;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link CurrencyServiceImpl}.
 *
 * <p>Design principles applied: - Method under test is {@link
 * CurrencyServiceImpl#getAllCurrencies()}. - External dependencies (data access via {@code list()})
 * are stubbed on a Spy to ensure isolation from database and MyBatis-Plus internals. - AAA
 * structure (Arrange/Act/Assert) and clear naming are used.
 */
@ExtendWith(MockitoExtension.class)
class CurrencyServiceImplTest {

  @Spy private CurrencyServiceImpl currencyService;

  private static Currency newCurrency(String code, Integer exponent) {
    Currency c = new Currency();
    c.setCode(code);
    c.setExponent(exponent);
    return c;
  }

  @Test
  @DisplayName("Typical: maps a list of currencies to response items in order")
  void getAllCurrencies_returnsMappedItems_givenTypicalCurrencies() {
    // Arrange
    List<Currency> currencies =
        Arrays.asList(newCurrency("USD", 2), newCurrency("JPY", 0), newCurrency("KWD", 3));
    doReturn(currencies).when(currencyService).list();

    // Act
    CurrencyResponse response = currencyService.getAllCurrencies();

    // Assert
    assertNotNull(response);
    assertNotNull(response.getItems());
    assertEquals(3, response.getItems().size());
    assertEquals("USD", response.getItems().get(0).getCode());
    assertEquals(2, response.getItems().get(0).getExponent());
    assertEquals("JPY", response.getItems().get(1).getCode());
    assertEquals(0, response.getItems().get(1).getExponent());
    assertEquals("KWD", response.getItems().get(2).getCode());
    assertEquals(3, response.getItems().get(2).getExponent());

    // Behavior verification: underlying list() called exactly once
    verify(currencyService, times(1)).list();
  }

  @Test
  @DisplayName("Atypical: empty list produces empty response items (loop count = 0)")
  void getAllCurrencies_emptyList_returnsEmptyItems() {
    // Arrange
    doReturn(Collections.emptyList()).when(currencyService).list();

    // Act
    CurrencyResponse response = currencyService.getAllCurrencies();

    // Assert
    assertNotNull(response);
    assertNotNull(response.getItems());
    assertEquals(0, response.getItems().size());
    verify(currencyService, times(1)).list();
  }

  @Test
  @DisplayName("Atypical: null exponent is preserved in mapping")
  void getAllCurrencies_nullExponent_preserved() {
    // Arrange
    doReturn(Collections.singletonList(newCurrency("XXX", null))).when(currencyService).list();

    // Act
    CurrencyResponse response = currencyService.getAllCurrencies();

    // Assert
    assertNotNull(response);
    assertEquals(1, response.getItems().size());
    assertEquals("XXX", response.getItems().get(0).getCode());
    assertNull(response.getItems().get(0).getExponent());
    verify(currencyService, times(1)).list();
  }

  @Test
  @DisplayName("Invalid: underlying list() throws and is propagated")
  void getAllCurrencies_whenListThrows_exceptionPropagates() {
    // Arrange
    doThrow(new RuntimeException("db down")).when(currencyService).list();

    // Act + Assert
    assertThrows(RuntimeException.class, () -> currencyService.getAllCurrencies());
    verify(currencyService, times(1)).list();
  }
}
