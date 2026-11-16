package com.icmp.claims.document.application;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One supporting document as returned by the API. The bytes are fetched from {@code contentUrl} with the same
 * bearer token. {@code sha256} is staff only (integrity checks), so it is omitted for claimants.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentView(UUID id, String category, String categoryLabel, String fileName, String contentType,
                           long sizeBytes, String uploadedByName, String uploadedByRole, Instant uploadedAt,
                           String contentUrl,
                           // staff only
                           String sha256) {
}
