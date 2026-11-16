package com.icmp.claims.document.application;

import java.io.InputStream;

/**
 * Where document bytes are kept (object storage). Implementations throw {@link StorageUnavailableException} when
 * the store cannot be reached, so the API can answer 503 instead of 500.
 */
public interface DocumentStoragePort {

    void put(String key, byte[] content, String contentType);

    InputStream open(String key);

    void delete(String key);

    class StorageUnavailableException extends RuntimeException {
        public StorageUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
