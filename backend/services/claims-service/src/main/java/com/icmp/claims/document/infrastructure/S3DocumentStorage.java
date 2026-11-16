package com.icmp.claims.document.infrastructure;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.icmp.claims.config.DocumentProperties;
import com.icmp.claims.document.application.DocumentStoragePort;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Stores document bytes in an S3-compatible bucket. The bucket is created on start-up if it is missing; if the
 * store is down at that moment the service still starts, and uploads answer 503 until it is back.
 */
@Component
public class S3DocumentStorage implements DocumentStoragePort {

    private static final Logger log = LoggerFactory.getLogger(S3DocumentStorage.class);

    private final S3Client s3;
    private final String bucket;

    public S3DocumentStorage(S3Client s3, DocumentProperties props) {
        this.s3 = s3;
        this.bucket = props.storage().bucket();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        try {
            createBucketIfMissing();
            log.info("Document bucket '{}' is ready", bucket);
        } catch (SdkException e) {
            log.warn("Document storage is not reachable yet ({}). Uploads will fail until it is.", e.getMessage());
        }
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            putObject(key, content, contentType);
        } catch (NoSuchBucketException e) {
            createBucketIfMissing();
            putObject(key, content, contentType);
        } catch (SdkException e) {
            throw unavailable(e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return s3.getObject(b -> b.bucket(bucket).key(key));
        } catch (SdkException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (SdkException e) {
            log.warn("Could not delete orphaned document object {}: {}", key, e.getMessage());
        }
    }

    private void putObject(String key, byte[] content, String contentType) {
        s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType).contentLength((long) content.length),
                RequestBody.fromBytes(content));
    }

    private void createBucketIfMissing() {
        try {
            s3.headBucket(b -> b.bucket(bucket));
        } catch (NoSuchBucketException e) {
            try {
                s3.createBucket(b -> b.bucket(bucket));
            } catch (BucketAlreadyOwnedByYouException alreadyThere) {
                // created by another instance in the meantime
            }
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            s3.createBucket(b -> b.bucket(bucket));
        }
    }

    private static StorageUnavailableException unavailable(SdkException e) {
        return new StorageUnavailableException("Document storage is unavailable: " + e.getMessage(), e);
    }
}
