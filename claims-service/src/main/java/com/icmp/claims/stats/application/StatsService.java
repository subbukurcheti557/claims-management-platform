package com.icmp.claims.stats.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.icmp.claims.claim.domain.ClaimStatus;
import com.icmp.claims.claim.domain.ClaimType;
import com.icmp.claims.claim.domain.CurrencyConverter;
import com.icmp.claims.claim.domain.Market;
import com.icmp.claims.claim.domain.Priority;
import com.icmp.claims.claim.infrastructure.persistence.ClaimFigures;
import com.icmp.claims.claim.infrastructure.persistence.ClaimRepository;
import com.icmp.claims.config.ClaimsProperties;
import com.icmp.claims.stats.application.StatsViews.AgeBucket;
import com.icmp.claims.stats.application.StatsViews.Exposure;
import com.icmp.claims.stats.application.StatsViews.ExposureTotals;
import com.icmp.claims.stats.application.StatsViews.MarketExposure;
import com.icmp.claims.stats.application.StatsViews.OfficerWorkload;
import com.icmp.claims.stats.application.StatsViews.StatusExposure;
import com.icmp.claims.stats.application.StatsViews.TypeExposure;
import com.icmp.claims.stats.application.StatsViews.Workload;

/**
 * Dashboard figures computed from a lightweight projection of the claims table. This is always consistent with
 * the claims themselves; at larger volumes the same numbers would come from SQL aggregates or a Kafka-fed
 * reporting store (the events already exist for it).
 */
@Service
@Transactional(readOnly = true)
public class StatsService {

    private static final List<Bucket> BUCKETS = List.of(new Bucket("0-7 days", 0, 7),
            new Bucket("8-30 days", 8, 30), new Bucket("31-90 days", 31, 90), new Bucket("90+ days", 91, Long.MAX_VALUE));

    private final ClaimRepository claims;
    private final CurrencyConverter converter;
    private final ClaimsProperties properties;
    private final Clock clock;

    public StatsService(ClaimRepository claims, CurrencyConverter converter, ClaimsProperties properties,
                        Clock clock) {
        this.claims = claims;
        this.converter = converter;
        this.properties = properties;
        this.clock = clock;
    }

    public Exposure exposure(Market marketFilter) {
        Instant now = Instant.now(clock);
        List<ClaimFigures> all = claims.findAllFigures().stream()
                .filter(f -> marketFilter == null || f.market() == marketFilter).toList();
        List<ClaimFigures> open = all.stream().filter(f -> f.status().isOpen()).toList();
        List<ClaimFigures> settled = all.stream().filter(f -> f.status() == ClaimStatus.SETTLED).toList();

        BigDecimal outstandingUsd = sumUsd(open, ClaimFigures::reserveAmount);
        BigDecimal paidUsd = sumUsd(settled, ClaimFigures::paidAmount);
        ExposureTotals totals = new ExposureTotals(open.size(), open.stream().filter(f -> f.isOverdue(now)).count(),
                settled.size(), outstandingUsd, paidUsd, outstandingUsd.add(paidUsd));

        List<MarketExposure> byMarket = new ArrayList<>();
        for (Market m : Market.values()) {
            List<ClaimFigures> mOpen = filter(open, f -> f.market() == m);
            List<ClaimFigures> mSettled = filter(settled, f -> f.market() == m);
            if (marketFilter != null && m != marketFilter) {
                continue;
            }
            BigDecimal outstanding = sum(mOpen, ClaimFigures::reserveAmount);
            BigDecimal paid = sum(mSettled, ClaimFigures::paidAmount);
            BigDecimal oUsd = converter.toUsd(outstanding, m.currency());
            BigDecimal pUsd = converter.toUsd(paid, m.currency());
            byMarket.add(new MarketExposure(m.name(), m.currency(), mOpen.size(), outstanding, oUsd, paid, pUsd,
                    oUsd.add(pUsd)));
        }

        List<TypeExposure> byType = new ArrayList<>();
        for (ClaimType t : ClaimType.values()) {
            List<ClaimFigures> tOpen = filter(open, f -> f.claimType() == t);
            byType.add(new TypeExposure(t.name(), tOpen.size(), sumUsd(tOpen, ClaimFigures::reserveAmount),
                    sumUsd(filter(settled, f -> f.claimType() == t), ClaimFigures::paidAmount)));
        }

        List<StatusExposure> byStatus = new ArrayList<>();
        for (ClaimStatus s : ClaimStatus.values()) {
            List<ClaimFigures> inStatus = filter(all, f -> f.status() == s);
            byStatus.add(new StatusExposure(s.name(), inStatus.size(),
                    s.isOpen() ? sumUsd(inStatus, ClaimFigures::reserveAmount) : BigDecimal.ZERO));
        }

        List<AgeBucket> ageing = new ArrayList<>();
        for (Bucket b : BUCKETS) {
            List<ClaimFigures> inBucket = filter(open, f -> b.contains(Duration.between(f.submittedAt(), now).toDays()));
            ageing.add(new AgeBucket(b.label(), inBucket.size(), sumUsd(inBucket, ClaimFigures::reserveAmount)));
        }

        return new Exposure(now, "USD", totals, byMarket, byType, byStatus, ageing,
                new TreeMap<>(properties.fxToUsd()));
    }

    public Workload workload(UUID onlyOfficer) {
        Instant now = Instant.now(clock);
        List<ClaimFigures> all = claims.findAllFigures();
        List<ClaimFigures> unassigned = filter(all, f -> f.assignedOfficerId() == null && f.status().isOpen());

        Map<UUID, List<ClaimFigures>> byOfficer = all.stream().filter(f -> f.assignedOfficerId() != null)
                .filter(f -> onlyOfficer == null || onlyOfficer.equals(f.assignedOfficerId()))
                .collect(Collectors.groupingBy(ClaimFigures::assignedOfficerId, LinkedHashMap::new,
                        Collectors.toList()));

        List<OfficerWorkload> officers = byOfficer.entrySet().stream()
                .map(e -> officerWorkload(e.getKey(), e.getValue(), now))
                .sorted(Comparator.comparing(OfficerWorkload::openClaims).reversed()
                        .thenComparing(OfficerWorkload::officerName, Comparator.nullsLast(String::compareTo)))
                .toList();
        return new Workload(now, unassigned.size(), unassigned.stream().filter(f -> f.isOverdue(now)).count(),
                officers);
    }

    private OfficerWorkload officerWorkload(UUID officerId, List<ClaimFigures> claimsOfOfficer, Instant now) {
        List<ClaimFigures> open = filter(claimsOfOfficer, f -> f.status().isOpen());
        Map<String, Long> openByStatus = new LinkedHashMap<>();
        for (ClaimStatus s : ClaimStatus.OPEN) {
            long count = open.stream().filter(f -> f.status() == s).count();
            if (count > 0) {
                openByStatus.put(s.name(), count);
            }
        }
        Instant thirtyDaysAgo = now.minus(Duration.ofDays(30));
        List<ClaimFigures> closedRecently = filter(claimsOfOfficer,
                f -> f.closedAt() != null && f.closedAt().isAfter(thirtyDaysAgo));
        List<ClaimFigures> closedWithin90 = filter(claimsOfOfficer,
                f -> f.closedAt() != null && f.closedAt().isAfter(now.minus(Duration.ofDays(90))));
        BigDecimal avgDays = closedWithin90.isEmpty() ? null : BigDecimal.valueOf(closedWithin90.stream()
                        .mapToLong(f -> Duration.between(f.submittedAt(), f.closedAt()).toHours()).average()
                        .orElse(0) / 24.0)
                .setScale(1, RoundingMode.HALF_UP);
        String name = claimsOfOfficer.stream().map(ClaimFigures::assignedOfficerName).filter(Objects::nonNull)
                .findFirst().orElse(null);
        return new OfficerWorkload(officerId, name, open.size(), openByStatus,
                open.stream().filter(f -> f.isOverdue(now)).count(),
                open.stream().filter(f -> f.priority() == Priority.HIGH).count(),
                closedRecently.size(), avgDays, sumUsd(open, ClaimFigures::reserveAmount));
    }

    private BigDecimal sumUsd(List<ClaimFigures> figures, Function<ClaimFigures, BigDecimal> amount) {
        return figures.stream().map(f -> converter.toUsd(nz(amount.apply(f)), f.currency()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<ClaimFigures> figures, Function<ClaimFigures, BigDecimal> amount) {
        return figures.stream().map(f -> nz(amount.apply(f))).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static List<ClaimFigures> filter(List<ClaimFigures> figures, Predicate<ClaimFigures> predicate) {
        return figures.stream().filter(predicate).toList();
    }

    private record Bucket(String label, long fromDays, long toDays) {
        boolean contains(long days) {
            return days >= fromDays && days <= toDays;
        }
    }
}
