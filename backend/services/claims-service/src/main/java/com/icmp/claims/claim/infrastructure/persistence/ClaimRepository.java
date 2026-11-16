package com.icmp.claims.claim.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.icmp.claims.claim.domain.Claim;
import com.icmp.claims.claim.domain.ClaimStatus;

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

    /** Claims already recorded for the same incident (duplicate check at FNOL). */
    List<Claim> findByPolicyNumberAndIncidentDate(String policyNumber, LocalDate incidentDate);

    /** What other claims on the policy have been approved or paid (policy-wide payout limit). */
    @Query("""
            SELECT COALESCE(SUM(c.approvedAmount), 0) FROM Claim c
            WHERE c.policyNumber = :policyNumber AND c.id <> :claimId AND c.status IN :statuses""")
    BigDecimal sumApprovedOnOtherClaims(@Param("policyNumber") String policyNumber, @Param("claimId") UUID claimId,
                                        @Param("statuses") Collection<ClaimStatus> statuses);

    /**
     * Serialises FNOL and approval per policy until the transaction ends (PostgreSQL advisory lock), so two
     * requests cannot both pass the duplicate check or both use the same remaining cover.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:policyNumber))) AS locked",
            nativeQuery = true)
    Integer lockPolicy(@Param("policyNumber") String policyNumber);
}
