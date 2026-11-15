package com.icmp.notification.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.icmp.notification.notification.domain.Notification.Category;

class NotificationTemplatesTest {

    static final UUID CLAIMANT = UUID.randomUUID();
    static final UUID OFFICER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();
    static final ObjectMapper JSON = new ObjectMapper();

    static ClaimEvent event(String type, UUID officer, UUID actor, ObjectNode data) {
        return new ClaimEvent(UUID.randomUUID(), type, 1, UUID.randomUUID(), "CLM-SG-2026-000100", CLAIMANT, officer,
                officer == null ? null : "Olivia Lim", "SGD", actor, data);
    }

    @Test
    void submittedTellsTheClaimant() {
        List<NotificationDraft> drafts = NotificationTemplates.render(event("ClaimSubmitted", null, CLAIMANT,
                JSON.createObjectNode()));
        assertThat(drafts).singleElement().satisfies(d -> {
            assertThat(d.recipientUserId()).isEqualTo(CLAIMANT);
            assertThat(d.title()).isEqualTo("Claim received");
        });
    }

    @Test
    void selfPickupTellsOnlyTheClaimant() {
        ObjectNode data = JSON.createObjectNode().put("reassigned", false);
        assertThat(NotificationTemplates.render(event("ClaimAssigned", OFFICER, OFFICER, data)))
                .extracting(NotificationDraft::recipientUserId).containsExactly(CLAIMANT);
    }

    @Test
    void managerAssignmentAlsoTellsTheOfficer() {
        ObjectNode data = JSON.createObjectNode().put("reassigned", false);
        assertThat(NotificationTemplates.render(event("ClaimAssigned", OFFICER, MANAGER, data)))
                .extracting(NotificationDraft::recipientUserId).containsExactly(CLAIMANT, OFFICER);
    }

    @Test
    void reassignmentTellsOnlyTheNewOfficer() {
        ObjectNode data = JSON.createObjectNode().put("reassigned", true);
        assertThat(NotificationTemplates.render(event("ClaimAssigned", OFFICER, MANAGER, data)))
                .extracting(NotificationDraft::recipientUserId).containsExactly(OFFICER);
    }

    @Test
    void infoRequestIsAnActionForTheClaimantWithTheQuestion() {
        ObjectNode data = JSON.createObjectNode().put("question", "Police report number?");
        assertThat(NotificationTemplates.render(event("InfoRequested", OFFICER, OFFICER, data))).singleElement()
                .satisfies(d -> {
                    assertThat(d.category()).isEqualTo(Category.ACTION_REQUIRED);
                    assertThat(d.message()).contains("Police report number?");
                });
    }

    @Test
    void infoProvidedGoesToTheAssignedOfficer() {
        assertThat(NotificationTemplates.render(event("InfoProvided", OFFICER, CLAIMANT, JSON.createObjectNode())))
                .extracting(NotificationDraft::recipientUserId).containsExactly(OFFICER);
    }

    @Test
    void settlementShowsFormattedAmountAndReference() {
        ObjectNode data = JSON.createObjectNode().put("amount", 7200).put("paymentReference", "PAY-1");
        assertThat(NotificationTemplates.render(event("ClaimSettled", OFFICER, OFFICER, data))).singleElement()
                .satisfies(d -> {
                    assertThat(d.category()).isEqualTo(Category.DECISION);
                    assertThat(d.message()).contains("SGD 7,200.00").contains("PAY-1");
                });
    }

    @Test
    void rejectionIncludesReasonAndExplanation() {
        ObjectNode data = JSON.createObjectNode().put("reasonDescription", "The loss is not covered by the policy.")
                .put("explanation", "Wear and tear is excluded.");
        assertThat(NotificationTemplates.render(event("ClaimRejected", OFFICER, OFFICER, data))).singleElement()
                .satisfies(d -> assertThat(d.message()).contains("not covered").contains("Wear and tear"));
    }

    @Test
    void withdrawalOfUnassignedClaimNotifiesNobody() {
        assertThat(NotificationTemplates.render(event("ClaimWithdrawn", null, CLAIMANT, JSON.createObjectNode())))
                .isEmpty();
    }

    @Test
    void internalAndUnknownEventsProduceNothing() {
        assertThat(NotificationTemplates.render(event("ClaimAssessed", OFFICER, OFFICER, JSON.createObjectNode())))
                .isEmpty();
        assertThat(NotificationTemplates.render(event("SomethingNew", OFFICER, OFFICER, JSON.createObjectNode())))
                .isEmpty();
    }
}
