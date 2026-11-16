package com.icmp.claims.document.domain;

import java.util.Optional;

/**
 * The file types claims accept, recognised by their first bytes ("magic numbers"). The browser's Content-Type and
 * the file name are chosen by the client, so neither is trusted: a renamed .exe is not a photo.
 */
public enum FileKind {
    JPEG("image/jpeg", "jpg", true),
    PNG("image/png", "png", true),
    WEBP("image/webp", "webp", true),
    PDF("application/pdf", "pdf", false);

    private final String contentType;
    private final String extension;
    private final boolean image;

    FileKind(String contentType, String extension, boolean image) {
        this.contentType = contentType;
        this.extension = extension;
        this.image = image;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public boolean isImage() {
        return image;
    }

    /** Recognises the file from its content, or empty if it is not one of the accepted types. */
    public static Optional<FileKind> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        if (startsWith(content, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        if (startsWith(content, 0, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, int... signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
