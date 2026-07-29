package com.dfs.metadata.domain.model;

import com.dfs.metadata.domain.exception.InvalidBucketNameException;

import java.util.regex.Pattern;

/**
 * DNS-compatible bucket name, following the S3 naming rules.
 */
public record BucketName(String value) {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 63;
    private static final Pattern VALID = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    public BucketName {
        if (value == null || value.isBlank()) {
            throw new InvalidBucketNameException("Bucket name must not be blank");
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new InvalidBucketNameException(
                    "Bucket name must be between %d and %d characters".formatted(MIN_LENGTH, MAX_LENGTH));
        }
        if (!VALID.matcher(value).matches()) {
            throw new InvalidBucketNameException(
                    "Bucket name must contain only lowercase letters, digits, and hyphens, "
                            + "and must start and end with a letter or digit");
        }
        if (value.contains("--")) {
            throw new InvalidBucketNameException("Bucket name must not contain consecutive hyphens");
        }
        if (IPV4.matcher(value).matches()) {
            throw new InvalidBucketNameException("Bucket name must not be formatted as an IP address");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
