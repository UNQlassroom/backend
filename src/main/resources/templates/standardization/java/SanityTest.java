import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Test de sanidad por defecto.
 */
class SanityTest {

  @Test
  void sanityTest() {
    Sanity sanity = new Sanity();
    assertTrue(sanity.check());
  }
}
