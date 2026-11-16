package com.icmp.claims.document.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.icmp.claims.document.domain.ClaimDocument;
import com.icmp.claims.document.domain.DocumentCategory;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, UUID> {

    List<ClaimDocument> findByClaimIdOrderByUploadedAtAsc(UUID claimId);

    Optional<ClaimDocument> findByIdAndClaimId(UUID id, UUID claimId);

    long countByClaimId(UUID claimId);

    long countByClaimIdAndCategory(UUID claimId, DocumentCategory category);

    /**
     * Serialises uploads to one claim until the transaction ends (PostgreSQL advisory lock), so two parallel
     * uploads cannot both pass the per-claim limit.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('documents:' || :claimId))) AS locked",
            nativeQuery = true)
    Integer lockClaimDocuments(@Param("claimId") String claimId);
}
