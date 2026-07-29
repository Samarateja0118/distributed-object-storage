package com.dfs.auth.domain.model;

import com.dfs.auth.domain.exception.InvalidEmailAddressException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalized to lower case on construction: the uniqueness index treats
 * addresses case-sensitively, so folding here is what stops the same person
 * registering twice with different capitalization.
 */
public record EmailAddress(String value) {

    private static final int MAX_LENGTH = 320;
    private static final Pattern VALID = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    public EmailAddress {
        if (value == null || value.isBlank()) {
            throw new InvalidEmailAddressException("Email address must not be blank");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH) {
            throw new InvalidEmailAddressException(
                    "Email address must not exceed %d characters".formatted(MAX_LENGTH));
        }
        if (!VALID.matcher(value).matches()) {
            throw new InvalidEmailAddressException("Email address is not well formed");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
