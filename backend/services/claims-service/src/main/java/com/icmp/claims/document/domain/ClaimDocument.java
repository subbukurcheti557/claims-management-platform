package com.icmp.claims.document.domain;

import java.time.Instant;
import java.util.UUID;

import com.icmp.claims.claim.domain.Actor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Metadata of one supporting document. The bytes live in object storage under {@link #getStorageKey()}; the
 * database keeps who uploaded what, when, and a SHA-256 of the content. Documents are evidence, so they are never
 * changed or deleted through the API.
 */
@Entity
@Table(name = "claim_document")
public class ClaimDocument {

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentCategory category;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 300)
    private String storageKey;

    @Column(name = "uploaded_by_id", nullable = false)
    private UUID uploadedById;

    @Column(name = "uploaded_by_name")
    private String uploadedByName;

    @Column(name = "uploaded_by_role", nullable = false, length = 30)
    private String uploadedByRole;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected ClaimDocument() {
        // JPA
    }

    public ClaimDocument(UUID id, UUID claimId, DocumentCategory category, String fileName, FileKind kind,
                         long sizeBytes, String sha256, String storageKey, Actor uploadedBy, Instant uploadedAt) {
        this.id = id;
        this.claimId = claimId;
        this.category = category;
        this.fileName = fileName;
        this.contentType = kind.contentType();
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.uploadedById = uploadedBy.id();
        this.uploadedByName = uploadedBy.name();
        this.uploadedByRole = uploadedBy.role();
        this.uploadedAt = uploadedAt;
    }

    /** Claimants see the documents they uploaded; documents added by staff are internal, like notes. */
    public boolean isVisibleTo(Actor actor) {
        return actor.isStaff() || (actor.isClaimant() && actor.id().equals(uploadedById));
    }

    public boolean isImage() {
        return contentType.startsWith("image/");
    }

    public UUID getId() {
        return id;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public DocumentCategory getCategory() {
        return category;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public UUID getUploadedById() {
        return uploadedById;
    }

    public String getUploadedByName() {
        return uploadedByName;
    }

    public String getUploadedByRole() {
        return uploadedByRole;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
