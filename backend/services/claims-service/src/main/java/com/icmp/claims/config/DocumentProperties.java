package com.icmp.claims.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Supporting documents (accident photos, reports) and the object store that holds them.
 *
 * @param maxFileSize              largest accepted file (keep in line with spring.servlet.multipart.max-file-size)
 * @param maxPerClaim              most documents one claim can hold
 * @param photoRequiredForApproval whether a claim needs at least one damage photo before it can be approved
 * @param storage                  S3-compatible object store (SeaweedFS locally, Amazon S3 in production)
 */
@ConfigurationProperties(prefix = "app.documents")
public record DocumentProperties(DataSize maxFileSize, int maxPerClaim, boolean photoRequiredForApproval,
                                 Storage storage) {

    /**
     * @param endpoint        S3 endpoint; leave empty to use Amazon S3 for the region
     * @param pathStyleAccess true for SeaweedFS and MinIO (bucket in the path, not the host name)
     */
    public record Storage(URI endpoint, String region, String bucket, String accessKey, String secretKey,
                          boolean pathStyleAccess) {
    }
}
