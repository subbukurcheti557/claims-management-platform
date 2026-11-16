package com.icmp.claims.document.domain;

/** What a supporting document shows. Damage photos must be images; the others may also be PDFs. */
public enum DocumentCategory {
    DAMAGE_PHOTO("Damage photo", true),
    POLICE_REPORT("Police report", false),
    REPAIR_ESTIMATE("Repair estimate", false),
    MEDICAL_REPORT("Medical report", false),
    OTHER("Other document", false);

    private final String label;
    private final boolean imagesOnly;

    DocumentCategory(String label, boolean imagesOnly) {
        this.label = label;
        this.imagesOnly = imagesOnly;
    }

    public String label() {
        return label;
    }

    public boolean imagesOnly() {
        return imagesOnly;
    }
}
