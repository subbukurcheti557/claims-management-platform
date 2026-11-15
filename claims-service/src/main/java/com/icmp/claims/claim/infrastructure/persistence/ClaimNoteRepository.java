package com.icmp.claims.claim.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.icmp.claims.claim.domain.ClaimNote;

public interface ClaimNoteRepository extends JpaRepository<ClaimNote, UUID> {

    List<ClaimNote> findByClaimIdOrderByCreatedAtDesc(UUID claimId);

    long countByClaimId(UUID claimId);
}
