package com.icmp.claims.claim.domain;

/** The six APAC markets and their local currency. */
public enum Market {
    SG("SGD"),
    HK("HKD"),
    AU("AUD"),
    MY("MYR"),
    TH("THB"),
    ID("IDR");

    private final String currency;

    Market(String currency) {
        this.currency = currency;
    }

    public String currency() {
        return currency;
    }
}
