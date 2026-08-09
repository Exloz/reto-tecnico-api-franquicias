package co.com.pragma.model.common;

import co.com.pragma.model.common.exception.InvalidNameException;
import co.com.pragma.model.common.exception.InvalidStockException;
import co.com.pragma.model.common.exception.InvalidVersionException;

import java.text.Normalizer;
import java.util.Locale;

public final class DomainRules {

    private DomainRules() {
    }

    public static NormalizedName normalizeName(String value, int maxLength) {
        if (value == null) {
            throw new InvalidNameException("Name is required");
        }

        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            throw new InvalidNameException("Name must not be blank");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > maxLength) {
            throw new InvalidNameException("Name must not exceed " + maxLength + " characters");
        }

        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return new NormalizedName(trimmed, normalized);
    }

    public static int initialStock(Integer stock) {
        return validateStock(stock == null ? 0 : stock);
    }

    public static int validateStock(int stock) {
        if (stock < 0) {
            throw new InvalidStockException(stock);
        }
        return stock;
    }

    public static long validateExpectedVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new InvalidVersionException(expectedVersion);
        }
        return expectedVersion;
    }
}
