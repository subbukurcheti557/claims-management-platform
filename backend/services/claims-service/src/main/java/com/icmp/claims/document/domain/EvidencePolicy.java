package com.icmp.claims.document.domain;

import java.util.EnumSet;
import java.util.Set;

import com.icmp.claims.claim.domain.Actor;
import com.icmp.claims.claim.domain.Claim;
import com.icmp.claims.claim.domain.ClaimRuleException;
import com.icmp.claims.claim.domain.ClaimStatus;

/**
 * Rules for supporting documents (accident photos, police reports, repair estimates...).
 *
 * <ul>
 *   <li>Only JPEG, PNG, WebP and PDF are accepted, recognised from the file's content; damage photos must be images.</li>
 *   <li>The claimant can add documents to their own claim until a decision is made (up to ASSESSED). Staff can add
 *       documents while the claim is open, if they handle it (assigned officer or any manager).</li>
 *   <li>A claim holds at most {@code maxPerClaim} documents of at most {@code maxFileBytes} each.</li>
 *   <li>When {@code photoRequiredForApproval} is on, a claim cannot be approved without at least one damage photo.</li>
 * </ul>
 */
public class EvidencePolicy {

    /** Statuses in which the claimant may still add evidence: before the decision. */
    private static final Set<ClaimStatus> CLAIMANT_CAN_ADD = EnumSet.of(ClaimStatus.SUBMITTED,
            ClaimStatus.UNDER_REVIEW, ClaimStatus.INFO_REQUESTED, ClaimStatus.ASSESSED);

    private final long maxFileBytes;
    private final int maxPerClaim;
    private final boolean photoRequiredForApproval;

    public EvidencePolicy(long maxFileBytes, int maxPerClaim, boolean photoRequiredForApproval) {
        this.maxFileBytes = maxFileBytes;
        this.maxPerClaim = maxPerClaim;
        this.photoRequiredForApproval = photoRequiredForApproval;
    }

    /** Checks the file itself and returns its real type. */
    public FileKind checkFile(DocumentCategory category, byte[] content) {
        if (content == null || content.length == 0) {
            throw ClaimRuleException.rule("The file is empty.");
        }
        if (content.length > maxFileBytes) {
            throw new ClaimRuleException(ClaimRuleException.Kind.FILE_TOO_LARGE, "The file is "
                    + megabytes(content.length) + " MB. The limit is " + megabytes(maxFileBytes) + " MB per file.");
        }
        FileKind kind = FileKind.detect(content).orElseThrow(() -> new ClaimRuleException(
                ClaimRuleException.Kind.UNSUPPORTED_FILE,
                "Only JPEG, PNG or WebP photos and PDF documents can be uploaded."));
        if (category.imagesOnly() && !kind.isImage()) {
            throw new ClaimRuleException(ClaimRuleException.Kind.UNSUPPORTED_FILE,
                    category.label() + "s must be JPEG, PNG or WebP images.");
        }
        return kind;
    }

    /** Checks that this person may add a document to this claim now. */
    public void checkCanAdd(Claim claim, Actor actor, long documentsOnClaim) {
        if (actor.isClaimant()) {
            if (!claim.isOwnedBy(actor.id())) {
                throw ClaimRuleException.notPermitted("Only the claimant who reported this claim can add documents.");
            }
            if (!CLAIMANT_CAN_ADD.contains(claim.getStatus())) {
                throw new ClaimRuleException(ClaimRuleException.Kind.INVALID_TRANSITION,
                        "Documents can no longer be added to claim " + claim.getClaimNumber() + " because it is "
                                + claim.getStatus() + ".");
            }
        } else if (actor.isStaff()) {
            if (claim.getStatus().isClosed()) {
                throw new ClaimRuleException(ClaimRuleException.Kind.INVALID_TRANSITION,
                        "Documents cannot be added to claim " + claim.getClaimNumber() + " because it is "
                                + claim.getStatus() + ".");
            }
            if (!actor.isManager() && !actor.id().equals(claim.getAssignedOfficerId())) {
                throw ClaimRuleException.notPermitted(
                        "Only the assigned officer or a claims manager can add documents to this claim.");
            }
        } else {
            throw ClaimRuleException.notPermitted("You cannot add documents to claims.");
        }
        if (documentsOnClaim >= maxPerClaim) {
            throw ClaimRuleException.rule("Claim " + claim.getClaimNumber() + " already has " + documentsOnClaim
                    + " documents, which is the limit.");
        }
    }

    /** Officers must see evidence of the damage before paying for it. */
    public void checkReadyForApproval(Claim claim, long damagePhotos) {
        if (photoRequiredForApproval && damagePhotos == 0) {
            throw ClaimRuleException.rule("Claim " + claim.getClaimNumber()
                    + " has no damage photos. Ask the claimant to upload photos of the damage before approving.");
        }
    }

    public long maxFileBytes() {
        return maxFileBytes;
    }

    private static String megabytes(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0));
    }
}
