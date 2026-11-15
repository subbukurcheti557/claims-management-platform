package com.icmp.claims.claim.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.icmp.claims.claim.domain.Claim;

public interface ClaimRepository extends JpaRepository<Claim, UUID>, JpaSpecificationExecutor<Claim> {

    @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimSequence();

    /** Lightweight rows for the workload and exposure dashboards. */
    @Query("""
            SELECT new com.icmp.claims.claim.infrastructure.persistence.ClaimFigures(
                c.market, c.currency, c.claimType, c.status, c.priority, c.assignedOfficerId, c.assignedOfficerName,
                c.reserveAmount, c.paidAmount, c.submittedAt, c.closedAt, c.slaDueAt)
            FROM Claim c""")
    List<ClaimFigures> findAllFigures();
}
