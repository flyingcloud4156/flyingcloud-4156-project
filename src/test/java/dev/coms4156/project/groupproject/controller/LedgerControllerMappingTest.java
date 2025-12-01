package dev.coms4156.project.groupproject.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Unit test to guard the regression fix in {@link LedgerController}: the mapping must be "/mine"
 * (not ":mine").
 *
 * <p>This test uses reflection only and does not start Spring.
 */
class LedgerControllerMappingTest {

  @Test
  void getMyLedgers_shouldBeMappedToMinePath() throws Exception {
    Method m = LedgerController.class.getDeclaredMethod("getMyLedgers");

    GetMapping gm = m.getAnnotation(GetMapping.class);
    assertNotNull(gm, "Expected @GetMapping on getMyLedgers()");

    String[] paths = gm.value();
    assertEquals(1, paths.length, "Expected exactly one mapping value");
    assertEquals("/mine", paths[0], "Expected mapping to be '/mine'");
  }
}
