package com.icmp.notification.notification.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.icmp.notification.notification.domain.Notification.Category;

/**
 * Who is told what for each claim event (Strategy: one template per event type). Events without a template,
 * such as ClaimAssessed, are internal and produce no notification. Map.of takes at most 10 entries; switch to
 * Map.ofEntries when adding more.
 */
public final class NotificationTemplates {

    private static final Map<String, Function<ClaimEvent, List<NotificationDraft>>> TEMPLATES = Map.of(
            "ClaimSubmitted", e -> List.of(toClaimant(e, Category.INFO, "Claim received",
                    "We have received claim " + e.claimNumber() + ". A claims officer will review it shortly.")),
            "ClaimAssigned", NotificationTemplates::assigned,
            "InfoRequested", e -> List.of(toClaimant(e, Category.ACTION_REQUIRED,
                    "Action required on " + e.claimNumber(),
                    "Your claims officer asked: " + e.text("question"))),
            "InfoProvided", e -> toOfficer(e, Category.ACTION_REQUIRED, "Claimant responded",
                    "The claimant answered your question on " + e.claimNumber() + "."),
            "ClaimApproved", e -> List.of(toClaimant(e, Category.DECISION, "Claim approved",
                    "Claim " + e.claimNumber() + " has been approved for " + e.money("amount")
                            + ". Payment will follow shortly.")),
            "ClaimSettled", e -> List.of(toClaimant(e, Category.DECISION, "Payment made",
                    "We have paid " + e.money("amount") + " for claim " + e.claimNumber() + " (reference "
                            + e.text("paymentReference") + ").")),
            "ClaimRejected", e -> List.of(toClaimant(e, Category.DECISION, "Claim decision",
                    "Claim " + e.claimNumber() + " was not approved. " + Objects.toString(e.text("reasonDescription"), "")
                            + " " + Objects.toString(e.text("explanation"), ""))),
            "ClaimWithdrawn", e -> toOfficer(e, Category.INFO, "Claim withdrawn",
                    "The claimant withdrew claim " + e.claimNumber() + "."),
            "DocumentUploaded", NotificationTemplates::documentUploaded);

    private NotificationTemplates() {
    }

    public static List<NotificationDraft> render(ClaimEvent event) {
        Function<ClaimEvent, List<NotificationDraft>> template = TEMPLATES.get(event.eventType());
        return template == null ? List.of() : template.apply(event);
    }

    private static List<NotificationDraft> assigned(ClaimEvent e) {
        List<NotificationDraft> drafts = new ArrayList<>();
        boolean reassigned = e.data() != null && e.data().path("reassigned").asBoolean(false);
        if (!reassigned) {
            drafts.add(toClaimant(e, Category.INFO, "Claims officer assigned",
                    e.assignedOfficerName() + " is now handling claim " + e.claimNumber() + "."));
        }
        // tell the officer only when someone else (a manager) gave them the claim
        if (e.assignedOfficerId() != null && !e.assignedOfficerId().equals(e.actorId())) {
            drafts.add(new NotificationDraft(e.assignedOfficerId(), Category.ACTION_REQUIRED,
                    "Claim assigned to you", "Claim " + e.claimNumber() + " has been assigned to you."));
        }
        return drafts;
    }

    /** New evidence from the claimant is something the officer should look at; staff uploads are internal. */
    private static List<NotificationDraft> documentUploaded(ClaimEvent e) {
        if (!"CLAIMANT".equals(e.text("uploadedByRole"))) {
            return List.of();
        }
        String what = Objects.toString(e.text("categoryLabel"), "document").toLowerCase(java.util.Locale.ROOT);
        return toOfficer(e, Category.ACTION_REQUIRED, "New evidence on " + e.claimNumber(),
                "The claimant uploaded a " + what + " (" + e.text("fileName") + ") to claim " + e.claimNumber() + ".");
    }

    private static NotificationDraft toClaimant(ClaimEvent e, Category category, String title, String message) {
        return new NotificationDraft(e.claimantId(), category, title, message.trim());
    }

    private static List<NotificationDraft> toOfficer(ClaimEvent e, Category category, String title, String message) {
        return e.assignedOfficerId() == null ? List.of()
                : List.of(new NotificationDraft(e.assignedOfficerId(), category, title, message));
    }
}
