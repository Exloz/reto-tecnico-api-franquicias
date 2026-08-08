package co.com.pragma.model.common;

import co.com.pragma.model.common.exception.InvalidNameException;
import co.com.pragma.model.common.exception.InvalidStockException;
import co.com.pragma.model.common.exception.InvalidVersionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainRulesTest {

    @Test
    void shouldTrimAndNormalizeCaseAndAccents() {
        NormalizedName name = DomainRules.normalizeName("  Café DEL Norte  ", 120);

        assertEquals("Café DEL Norte", name.value());
        assertEquals("cafe del norte", name.normalized());
    }

    @Test
    void shouldCountUnicodeCodePointsForMaximumLength() {
        NormalizedName name = DomainRules.normalizeName("😀", 1);

        assertEquals("😀", name.value());
    }

    @Test
    void shouldRejectMissingBlankAndLongNames() {
        assertThrows(InvalidNameException.class, () -> DomainRules.normalizeName(null, 120));
        assertThrows(InvalidNameException.class, () -> DomainRules.normalizeName("   ", 120));
        assertThrows(InvalidNameException.class, () -> DomainRules.normalizeName("abc", 2));
    }

    @Test
    void shouldDefaultInitialStockToZero() {
        assertEquals(0, DomainRules.initialStock(null));
        assertEquals(0, DomainRules.initialStock(0));
        assertEquals(25, DomainRules.initialStock(25));
    }

    @Test
    void shouldRejectNegativeStock() {
        assertThrows(InvalidStockException.class, () -> DomainRules.initialStock(-1));
        assertThrows(InvalidStockException.class, () -> DomainRules.validateStock(-1));
    }

    @Test
    void shouldRejectNegativeExpectedVersion() {
        assertEquals(0, DomainRules.validateExpectedVersion(0));
        assertThrows(InvalidVersionException.class, () -> DomainRules.validateExpectedVersion(-1));
    }
}
