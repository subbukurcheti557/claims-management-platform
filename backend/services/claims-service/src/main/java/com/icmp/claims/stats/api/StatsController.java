package com.icmp.claims.stats.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.icmp.claims.claim.domain.Market;
import com.icmp.claims.common.security.CurrentUser;
import com.icmp.claims.stats.application.StatsService;
import com.icmp.claims.stats.application.StatsViews.Exposure;
import com.icmp.claims.stats.application.StatsViews.OfficerWorkload;
import com.icmp.claims.stats.application.StatsViews.Workload;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/claims/stats")
@PreAuthorize("hasAnyRole('CLAIMS_OFFICER','CLAIMS_MANAGER')")
@Tag(name = "Claims - dashboards")
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/exposure")
    @Operation(summary = "Outstanding, paid and incurred liability by market, type, status and age")
    public Exposure exposure(@RequestParam(required = false) Market market) {
        return stats.exposure(market);
    }

    @GetMapping("/workload")
    @Operation(summary = "Team workload and performance per officer")
    public Workload workload() {
        return stats.workload(null);
    }

    @GetMapping("/me")
    @Operation(summary = "The calling officer's own workload figures")
    public OfficerWorkload me() {
        CurrentUser user = CurrentUser.get();
        return stats.workload(user.id()).officers().stream().findFirst()
                .orElse(new OfficerWorkload(user.id(), user.name(), 0, java.util.Map.of(), 0, 0, 0, null,
                        java.math.BigDecimal.ZERO));
    }
}
