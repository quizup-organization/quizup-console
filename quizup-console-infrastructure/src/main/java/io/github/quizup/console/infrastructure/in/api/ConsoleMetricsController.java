package io.github.quizup.console.infrastructure.in.api;

import io.github.quizup.console.application.service.ConsoleMetricsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Séries Prometheus des écrans (JVM, processors, messages). Les requêtes PromQL sont fixées côté
 * service — aucune requête arbitraire n'est exposée.
 */
@RestController
@RequestMapping("/api/console/metrics")
@PreAuthorize("hasRole('ADMIN')")
public class ConsoleMetricsController {

    private final ConsoleMetricsService metricsService;

    public ConsoleMetricsController(ConsoleMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/applications/{application}")
    public ConsoleMetricsService.ApplicationMetrics applicationMetrics(
            @PathVariable String application,
            @RequestParam(defaultValue = "1h") String range) {
        return metricsService.applicationMetrics(application, range);
    }

    @GetMapping("/processors/{application}/{instanceId}/{name}")
    public ConsoleMetricsService.ProcessorMetrics processorMetrics(
            @PathVariable String application,
            @PathVariable String instanceId,
            @PathVariable String name,
            @RequestParam(defaultValue = "1h") String range) {
        return metricsService.processorMetrics(application, name, range);
    }

    @GetMapping("/messages/{application}")
    public ConsoleMetricsService.MessageMetrics messageMetrics(
            @PathVariable String application,
            @RequestParam(defaultValue = "1h") String range) {
        return metricsService.messageMetrics(application, range);
    }
}
