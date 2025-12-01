package dev.coms4156.project.groupproject.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

/**
 * Unit tests for {@link CorsConfig}.
 *
 * <p>We do not start Spring. Instead we call {@link CorsConfig#addCorsMappings(CorsRegistry)} and
 * then use reflection to inspect the internal {@link CorsRegistration} and its {@link
 * org.springframework.web.cors.CorsConfiguration}.
 */
class CorsConfigTest {

  @Test
  void corsConfig_shouldExposeExpectedOriginsMethodsHeadersAndCredentials() throws Exception {
    CorsRegistry registry = new CorsRegistry();
    new CorsConfig().addCorsMappings(registry);

    // CorsRegistry keeps a private List<CorsRegistration> registrations
    Field f = CorsRegistry.class.getDeclaredField("registrations");
    f.setAccessible(true);

    @SuppressWarnings("unchecked")
    List<CorsRegistration> regs = (List<CorsRegistration>) f.get(registry);
    assertNotNull(regs);
    assertEquals(1, regs.size(), "Expected exactly one CORS registration");

    CorsRegistration reg = regs.get(0);

    Field cf = CorsRegistration.class.getDeclaredField("config");
    cf.setAccessible(true);
    org.springframework.web.cors.CorsConfiguration cfg =
        (org.springframework.web.cors.CorsConfiguration) cf.get(reg);

    assertNotNull(cfg);

    // Origins
    assertNotNull(cfg.getAllowedOriginPatterns());
    assertTrue(cfg.getAllowedOriginPatterns().contains("http://localhost:[*]"));
    assertTrue(cfg.getAllowedOriginPatterns().contains("http://127.0.0.1:[*]"));

    // Methods
    assertNotNull(cfg.getAllowedMethods());
    assertTrue(
        cfg.getAllowedMethods().containsAll(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")));

    // Headers and exposed headers
    assertNotNull(cfg.getExposedHeaders());
    assertTrue(cfg.getExposedHeaders().contains("X-Auth-Token"));

    assertTrue(Boolean.TRUE.equals(cfg.getAllowCredentials()), "Expected allowCredentials=true");
  }
}
