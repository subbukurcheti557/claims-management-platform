package com.icmp.claims.document.api;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.icmp.claims.common.security.CurrentUser;
import com.icmp.claims.document.application.DocumentService;
import com.icmp.claims.document.application.DocumentService.DocumentContent;
import com.icmp.claims.document.application.DocumentView;
import com.icmp.claims.document.domain.DocumentCategory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Accident photos and supporting documents, for both user types. Claimants work on their own claims and see only
 * what they uploaded; staff see every document. Downloads go through this service, so the same access rules apply
 * to the bytes as to the metadata and the object store is never exposed to the browser.
 */
@RestController
@RequestMapping("/api/claims/{claimId}/documents")
@Tag(name = "Claims - documents")
public class ClaimDocumentController {

    private final DocumentService documents;

    public ClaimDocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a photo (JPEG/PNG/WebP) or PDF, up to 10 MB. 415 for other types, 413 if too large.")
    public ResponseEntity<DocumentView> upload(@PathVariable UUID claimId,
                                               @RequestParam DocumentCategory category,
                                               @RequestPart("file") MultipartFile file) throws IOException {
        DocumentView created = documents.upload(claimId, category, file.getOriginalFilename(), file.getBytes(),
                CurrentUser.get());
        return ResponseEntity.created(URI.create(created.contentUrl())).body(created);
    }

    @GetMapping
    @Operation(summary = "Documents on the claim, oldest first")
    public List<DocumentView> list(@PathVariable UUID claimId) {
        return documents.list(claimId, CurrentUser.get());
    }

    @GetMapping("/{documentId}/content")
    @Operation(summary = "The document's bytes, with its verified content type")
    public ResponseEntity<InputStreamResource> content(@PathVariable UUID claimId, @PathVariable UUID documentId,
                                                       @RequestParam(defaultValue = "false") boolean download) {
        DocumentContent doc = documents.open(claimId, documentId, CurrentUser.get());
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(doc.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.contentType()))
                .contentLength(doc.sizeBytes())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'")
                .body(new InputStreamResource(doc.content()));
    }
}
