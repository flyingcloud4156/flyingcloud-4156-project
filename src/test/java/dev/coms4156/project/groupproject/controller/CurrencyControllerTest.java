package dev.coms4156.project.groupproject.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.coms4156.project.groupproject.dto.CurrencyResponse;
import dev.coms4156.project.groupproject.service.CurrencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CurrencyControllerTest {

  private MockMvc mockMvc;
  @Mock private CurrencyService currencyService;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.standaloneSetup(new CurrencyController(currencyService)).build();
  }

  @Test
  @DisplayName("GET /api/v1/currencies -> 200 and body")
  void getAllCurrencies_returnsOk() throws Exception {
    given(currencyService.getAllCurrencies()).willReturn(new CurrencyResponse());
    mockMvc
        .perform(get("/api/v1/currencies"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }
}
