package com.icmp.claims.document.application;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.icmp.claims.claim.application.port.EventOutboxPort;
import com.icmp.claims.claim.domain.Actor;
import com.icmp.claims.claim.domain.Claim;
import com.icmp.claims.claim.domain.ClaimDomainEvent;
import com.icmp.claims.claim.infrastructure.persistence.ClaimRepository;
import com.icmp.claims.common.error.ApiException;
import com.icmp.claims.common.security.CurrentUser;
import com.icmp.claims.document.domain.ClaimDocument;
import com.icmp.claims.document.domain.DocumentCategory;
import com.icmp.claims.document.domain.EvidencePolicy;
import com.icmp.claims.document.domain.FileKind;
import com.icmp.claims.document.infrastructure.ClaimDocumentRepository;

/**
 * Upload, list and download supporting documents.
 *
 * <p>Upload order matters because the bytes and the row live in different stores: the bytes go to object storage
 * first, then the row and the DocumentUploaded event commit in one transaction. If that transaction fails the
 * object is deleted again, so the database never points at a missing file.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final ClaimRepository claims;
    private final ClaimDocumentRepository documents;
    private final DocumentStoragePort storage;
    private final EventOutboxPort outbox;
    private final EvidencePolicy evidence;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DocumentService(ClaimRepository claims, ClaimDocumentRepository documents, DocumentStoragePort storage,
                           EventOutboxPort outbox, EvidencePolicy evidence, TransactionTemplate tx, Clock clock) {
        this.claims = claims;
        this.documents = documents;
        this.storage = storage;
        this.outbox = outbox;
        this.evidence = evidence;
        this.tx = tx;
        this.clock = clock;
    }

    public DocumentView upload(UUID claimId, DocumentCategory category, String originalName, byte[] content,
                               CurrentUser user) {
        Actor actor = actor(user);
        FileKind kind = evidence.checkFile(category, content);
        String fileName = safeFileName(originalName, kind);

        // 1. fail fast (wrong claim, status or limit) before any bytes are stored
        tx.executeWithoutResult(status ->
                evidence.checkCanAdd(load(claimId, user), actor, documents.countByClaimId(claimId)));

        // 2. bytes to object storage, outside the database transaction
        UUID documentId = UUID.randomUUID();
        String key = "claims/%s/%s.%s".formatted(claimId, documentId, kind.extension());
        storage.put(key, content, kind.contentType());

        // 3. metadata + event in one transaction; checks run again under a per-claim lock
        try {
            return tx.execute(status -> {
                documents.lockClaimDocuments(claimId.toString());
                Claim claim = load(claimId, user);
                evidence.checkCanAdd(claim, actor, documents.countByClaimId(claimId));
                ClaimDocument document = documents.save(new ClaimDocument(documentId, claimId, category, fileName,
                        kind, content.length, sha256(content), key, actor, Instant.now(clock)));
                outbox.append(claim, List.of(ClaimDomainEvent.of(ClaimDomainEvent.DOCUMENT_UPLOADED, actor,
                        eventData(document))));
                log.info("{} {} added {} '{}' ({} bytes) to claim {}", actor.role(), actor.id(), category, fileName,
                        content.length, claim.getClaimNumber());
                return view(document, user.isStaff());
            });
        } catch (RuntimeException e) {
            storage.delete(key);
            throw e;
        }
    }

    public List<DocumentView> list(UUID claimId, CurrentUser user) {
        Actor actor = actor(user);
        return tx.execute(status -> {
            load(claimId, user);
            return documents.findByClaimIdOrderByUploadedAtAsc(claimId).stream()
                    .filter(d -> d.isVisibleTo(actor))
                    .map(d -> view(d, user.isStaff()))
                    .toList();
        });
    }

    /** The document's metadata and an open stream of its bytes; the caller must close the stream. */
    public DocumentContent open(UUID claimId, UUID documentId, CurrentUser user) {
        Actor actor = actor(user);
        ClaimDocument document = tx.execute(status -> {
            load(claimId, user);
            return documents.findByIdAndClaimId(documentId, claimId)
                    .filter(d -> d.isVisibleTo(actor))
                    .orElseThrow(() -> ApiException.notFound("Document " + documentId + " was not found."));
        });
        return new DocumentContent(document.getFileName(), document.getContentType(), document.getSizeBytes(),
                storage.open(document.getStorageKey()));
    }

    public record DocumentContent(String fileName, String contentType, long sizeBytes, InputStream content) {
    }

    // ------------------------------------------------------------------ internals

    /** Staff see any claim; claimants only their own, and a foreign claim is reported as not found. */
    private Claim load(UUID claimId, CurrentUser user) {
        return claims.findById(claimId)
                .filter(c -> user.isStaff() || c.isOwnedBy(user.id()))
                .orElseThrow(() -> ApiException.notFound("Claim " + claimId + " was not found."));
    }

    private static DocumentView view(ClaimDocument d, boolean staffView) {
        return new DocumentView(d.getId(), d.getCategory().name(), d.getCategory().label(), d.getFileName(),
                d.getContentType(), d.getSizeBytes(), d.getUploadedByName(), d.getUploadedByRole(),
                d.getUploadedAt(), "/api/claims/" + d.getClaimId() + "/documents/" + d.getId() + "/content",
                staffView ? d.getSha256() : null);
    }

    private static Map<String, Object> eventData(ClaimDocument d) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("documentId", d.getId());
        data.put("category", d.getCategory().name());
        data.put("categoryLabel", d.getCategory().label());
        data.put("fileName", d.getFileName());
        data.put("contentType", d.getContentType());
        data.put("sizeBytes", d.getSizeBytes());
        data.put("uploadedByRole", d.getUploadedByRole());
        return data;
    }

    /**
     * Keeps only the last path segment, drops control and path characters, limits the length and makes the
     * extension match the real file type (so "photo.exe" that is really a JPEG is stored as "photo.jpg").
     */
    static String safeFileName(String original, FileKind kind) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>:|?*]", "").trim();
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        if (base.isBlank()) {
            base = "document";
        }
        int maxBase = 200 - kind.extension().length() - 1;
        if (base.length() > maxBase) {
            base = base.substring(0, maxBase);
        }
        return base + "." + kind.extension();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static Actor actor(CurrentUser user) {
        return new Actor(user.id(), user.name(), user.role());
    }
}
