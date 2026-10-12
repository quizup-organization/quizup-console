package io.github.quizup.console.infrastructure.in.api;

import io.github.quizup.console.application.service.ConsoleDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tableau de bord (contenu natif Axon Server : applications/instances, processors, DLQ, débits,
 * volumétrie event store, version).
 */
@RestController
@RequestMapping("/api/console")
@PreAuthorize("hasRole('ADMIN')")
public class ConsoleDashboardController {

    private final ConsoleDashboardService dashboardService;

    public ConsoleDashboardController(ConsoleDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ConsoleDashboardService.DashboardResponse dashboard() {
        return dashboardService.dashboard();
    }
}
