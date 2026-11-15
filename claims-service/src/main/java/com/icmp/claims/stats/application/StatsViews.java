package com.icmp.claims.stats.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StatsViews {

    private StatsViews() {
    }

    /** Liability exposure. Outstanding = reserves on open claims; paid = settled payments; incurred = both. */
    public record Exposure(Instant asOf, String reportingCurrency, ExposureTotals totals, List<MarketExposure> byMarket,
                           List<TypeExposure> byClaimType, List<StatusExposure> byStatus, List<AgeBucket> ageing,
                           Map<String, BigDecimal> fxRatesToUsd) {
    }

    public record ExposureTotals(long openClaims, long overdueClaims, long settledClaims, BigDecimal outstandingUsd,
                                 BigDecimal paidUsd, BigDecimal incurredUsd) {
    }

    public record MarketExposure(String market, String currency, long openClaims, BigDecimal outstanding,
                                 BigDecimal outstandingUsd, BigDecimal paid, BigDecimal paidUsd,
                                 BigDecimal incurredUsd) {
    }

    public record TypeExposure(String claimType, long openClaims, BigDecimal outstandingUsd, BigDecimal paidUsd) {
    }

    public record StatusExposure(String status, long claims, BigDecimal outstandingUsd) {
    }

    public record AgeBucket(String bucket, long openClaims, BigDecimal outstandingUsd) {
    }

    /** Team workload and performance. */
    public record Workload(Instant asOf, long unassignedClaims, long unassignedOverdue, List<OfficerWorkload> officers) {
    }

    public record OfficerWorkload(UUID officerId, String officerName, long openClaims, Map<String, Long> openByStatus,
                                  long overdueClaims, long highPriorityOpen, long closedLast30Days,
                                  BigDecimal averageDaysToClose, BigDecimal outstandingUsd) {
    }
}
