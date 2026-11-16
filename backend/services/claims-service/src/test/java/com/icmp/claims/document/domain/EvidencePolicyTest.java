package com.icmp.claims.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.icmp.claims.claim.domain.Actor;
import com.icmp.claims.claim.domain.AuthorityPolicy;
import com.icmp.claims.claim.domain.Claim;
import com.icmp.claims.claim.domain.ClaimRuleException;
import com.icmp.claims.claim.domain.ClaimRuleException.Kind;
import com.icmp.claims.claim.domain.ClaimType;
import com.icmp.claims.claim.domain.Liability;
import com.icmp.claims.claim.domain.Market;
import com.icmp.claims.claim.domain.Priority;
import com.icmp.claims.claim.domain.RejectionReason;

class EvidencePolicyTest {

    static final Actor ALICE = new Actor(UUID.randomUUID(), "Alice", Actor.CLAIMANT);
    static final Actor BOB = new Actor(UUID.randomUUID(), "Bob", Actor.CLAIMANT);
    static final Actor OLIVIA = new Actor(UUID.randomUUID(), "Olivia", Actor.CLAIMS_OFFICER);
    static final Actor OMAR = new Actor(UUID.randomUUID(), "Omar", Actor.CLAIMS_OFFICER);
    static final Actor MAYA = new Actor(UUID.randomUUID(), "Maya", Actor.CLAIMS_MANAGER);
    static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    static final byte[] PDF = "%PDF-1.7\n%âã".getBytes(StandardCharsets.ISO_8859_1);

    // SGD to USD at 0.74; officer limit USD 25,000
    static final AuthorityPolicy AUTHORITY = new AuthorityPolicy(new BigDecimal("25000"),
            (amount, currency) -> amount.multiply(new BigDecimal("0.74")));

    final EvidencePolicy policy = new EvidencePolicy(1024, 3, true);

    static Claim claim() {
        return Claim.submit("CLM-SG-2026-000200",
                new Claim.NewClaim("SG-MOT-1001", ClaimType.MOTOR, LocalDate.of(2026, 11, 10), "Orchard Road",
                        "Rear-ended at the traffic lights", false, new BigDecimal("8000")),
                new Claim.CoverageSnapshot(Market.SG, "SGD", new BigDecimal("60000"), new BigDecimal("500"), "Car"),
                Priority.LOW, ALICE, NOW);
    }

    @Nested
    class Files {

        @Test
        void recognisesAcceptedTypesFromTheirContent() {
            assertThat(FileKind.detect(JPEG)).contains(FileKind.JPEG);
            assertThat(FileKind.detect(PNG)).contains(FileKind.PNG);
            assertThat(FileKind.detect(WEBP)).contains(FileKind.WEBP);
            assertThat(FileKind.detect(PDF)).contains(FileKind.PDF);
        }

        @Test
        void rejectsAnythingElseWhateverItIsCalled() {
            byte[] exe = {'M', 'Z', (byte) 0x90, 0, 3, 0};
            byte[] html = "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
            byte[] riffNotWebp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
            assertThat(FileKind.detect(exe)).isEmpty();
            assertThat(FileKind.detect(html)).isEmpty();
            assertThat(FileKind.detect(riffNotWebp)).isEmpty();
            assertThat(FileKind.detect(new byte[] {(byte) 0xFF})).isEmpty();
            assertThatThrownBy(() -> policy.checkFile(DocumentCategory.OTHER, exe))
                    .isInstanceOf(ClaimRuleException.class)
                    .satisfies(e -> assertThat(((ClaimRuleException) e).getKind()).isEqualTo(Kind.UNSUPPORTED_FILE));
        }

        @Test
        void damagePhotosMustBeImagesButReportsMayBePdf() {
            assertThat(policy.checkFile(DocumentCategory.DAMAGE_PHOTO, JPEG)).isEqualTo(FileKind.JPEG);
            assertThat(policy.checkFile(DocumentCategory.POLICE_REPORT, PDF)).isEqualTo(FileKind.PDF);
            assertThatThrownBy(() -> policy.checkFile(DocumentCategory.DAMAGE_PHOTO, PDF))
                    .hasMessageContaining("Damage photos must be JPEG, PNG or WebP images");
        }

        @Test
        void emptyAndOversizedFilesAreRefused() {
            assertThatThrownBy(() -> policy.checkFile(DocumentCategory.OTHER, new byte[0]))
                    .hasMessage("The file is empty.");
            byte[] big = Arrays.copyOf(JPEG, 2048);
            assertThatThrownBy(() -> policy.checkFile(DocumentCategory.DAMAGE_PHOTO, big))
                    .satisfies(e -> assertThat(((ClaimRuleException) e).getKind()).isEqualTo(Kind.FILE_TOO_LARGE))
                    .hasMessageContaining("limit");
        }
    }

    @Nested
    class WhoCanAdd {

        @Test
        void claimantAddsToOwnClaimUntilTheDecision() {
            Claim claim = claim();
            assertThatCode(() -> policy.checkCanAdd(claim, ALICE, 0)).doesNotThrowAnyException();
            claim.assign(OLIVIA.id(), OLIVIA.name(), OLIVIA, NOW);
            claim.assess(Liability.ACCEPTED, new BigDecimal("7000"), new BigDecimal("7000"), "Quote checked",
                    OLIVIA, NOW);
            assertThatCode(() -> policy.checkCanAdd(claim, ALICE, 0)).doesNotThrowAnyException();
            claim.approve(new BigDecimal("7000"), AUTHORITY, OLIVIA, NOW);
            assertThatThrownBy(() -> policy.checkCanAdd(claim, ALICE, 0))
                    .satisfies(e -> assertThat(((ClaimRuleException) e).getKind())
                            .isEqualTo(Kind.INVALID_TRANSITION))
                    .hasMessageContaining("because it is APPROVED");
        }

        @Test
        void anotherClaimantCannotAdd() {
            assertThatThrownBy(() -> policy.checkCanAdd(claim(), BOB, 0))
                    .satisfies(e -> assertThat(((ClaimRuleException) e).getKind()).isEqualTo(Kind.NOT_PERMITTED));
        }

        @Test
        void staffMustHandleTheClaimAndItMustBeOpen() {
            Claim claim = claim();
            assertThatThrownBy(() -> policy.checkCanAdd(claim, OLIVIA, 0))
                    .hasMessageContaining("assigned officer or a claims manager");
            assertThatCode(() -> policy.checkCanAdd(claim, MAYA, 0)).doesNotThrowAnyException();
            claim.assign(OLIVIA.id(), OLIVIA.name(), OLIVIA, NOW);
            assertThatCode(() -> policy.checkCanAdd(claim, OLIVIA, 0)).doesNotThrowAnyException();
            assertThatThrownBy(() -> policy.checkCanAdd(claim, OMAR, 0))
                    .satisfies(e -> assertThat(((ClaimRuleException) e).getKind()).isEqualTo(Kind.NOT_PERMITTED));
            claim.reject(RejectionReason.OTHER, "Not a covered event", OLIVIA, NOW);
            assertThatThrownBy(() -> policy.checkCanAdd(claim, OLIVIA, 0))
                    .hasMessageContaining("because it is REJECTED");
        }

        @Test
        void aClaimHoldsAtMostTheConfiguredNumberOfDocuments() {
            assertThatCode(() -> policy.checkCanAdd(claim(), ALICE, 2)).doesNotThrowAnyException();
            assertThatThrownBy(() -> policy.checkCanAdd(claim(), ALICE, 3))
                    .satisfies(e -> assertThat(((ClaimRuleException) e).getKind()).isEqualTo(Kind.RULE_VIOLATION))
                    .hasMessageContaining("already has 3 documents");
        }
    }

    @Nested
    class Approval {

        @Test
        void needsAtLeastOneDamagePhoto() {
            assertThatThrownBy(() -> policy.checkReadyForApproval(claim(), 0))
                    .satisfies(e -> assertThat(((ClaimRuleException) e).getKind()).isEqualTo(Kind.RULE_VIOLATION))
                    .hasMessageContaining("has no damage photos");
            assertThatCode(() -> policy.checkReadyForApproval(claim(), 1)).doesNotThrowAnyException();
        }

        @Test
        void ruleCanBeSwitchedOff() {
            assertThatCode(() -> new EvidencePolicy(1024, 3, false).checkReadyForApproval(claim(), 0))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class Visibility {

        @Test
        void claimantSeesOwnUploadsAndStaffSeeEverything() {
            ClaimDocument byAlice = document(ALICE);
            ClaimDocument byOlivia = document(OLIVIA);
            assertThat(byAlice.isVisibleTo(ALICE)).isTrue();
            assertThat(byOlivia.isVisibleTo(ALICE)).isFalse();
            assertThat(byAlice.isVisibleTo(BOB)).isFalse();
            assertThat(byAlice.isVisibleTo(OMAR)).isTrue();
            assertThat(byOlivia.isVisibleTo(MAYA)).isTrue();
        }

        private ClaimDocument document(Actor by) {
            return new ClaimDocument(UUID.randomUUID(), UUID.randomUUID(), DocumentCategory.DAMAGE_PHOTO, "a.jpg",
                    FileKind.JPEG, 10, "0".repeat(64), "k/" + UUID.randomUUID(), by, NOW);
        }
    }
}
