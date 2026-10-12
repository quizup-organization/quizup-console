package io.github.quizup.console.infrastructure.in.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quizup.console.application.service.ConsoleMetricsService;
import io.github.quizup.console.infrastructure.out.client.ServiceActionClient;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleInstanceJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Recherche (contenu natif Axon Server) : événements par agrégat, recherche par type
 * <b>multi-applications</b> avec fenêtre temporelle, snapshots, scheduled events. Les types
 * d'événements connus sont proposés depuis Prometheus (label `name`).
 */
@RestController
@RequestMapping("/api/console")
@PreAuthorize("hasRole('ADMIN')")
public class ConsoleSearchController {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleSearchController.class);
    private static final int MAX_LIMIT = 200;

    private final ServiceActionClient client;
    private final ConsoleInstanceJpaRepository instanceRepository;
    private final ConsoleMetricsService metricsService;
    private final ObjectMapper objectMapper;

    public ConsoleSearchController(ServiceActionClient client,
                                   ConsoleInstanceJpaRepository instanceRepository,
                                   ConsoleMetricsService metricsService,
                                   ObjectMapper objectMapper) {
        this.client = client;
        this.instanceRepository = instanceRepository;
        this.metricsService = metricsService;
        this.objectMapper = objectMapper;
    }

    /** Types d'événements connus (filtre de la page Recherche). */
    @GetMapping("/search/event-types")
    public List<String> eventTypes() {
        return metricsService.eventTypes();
    }

    @GetMapping("/search/events")
    public ResponseEntity<String> events(@RequestParam String application,
                                         @RequestParam String aggregateId,
                                         @RequestParam(required = false) Long minSequence) {
        String path = "/eventstore/aggregates/" + segment(aggregateId) + "/events"
                + (minSequence == null ? "" : "?minSequence=" + minSequence);
        return proxy(application, path);
    }

    @GetMapping("/search/events/by-type")
    public List<EventSearchResult> eventsByType(@RequestParam(required = false) String application,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(defaultValue = "24h") String within,
                                                @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        Instant cutoff = cutoff(within);
        List<String> applications = application == null || application.isBlank()
                ? distinctApplications()
                : List.of(application);

        List<EventSearchResult> results = new ArrayList<>();
        for (String candidate : applications) {
            try {
                String path = "/eventstore/events?limit=" + safeLimit
                        + (type == null || type.isBlank()
                        ? "" : "&type=" + UriUtils.encodeQueryParam(type, StandardCharsets.UTF_8));
                JsonNode entries = objectMapper.readTree(client.get(candidate, path));
                for (JsonNode entry : entries) {
                    EventSearchResult result = toResult(candidate, entry);
                    if (cutoff == null || result.timestamp() == null || result.timestamp().isAfter(cutoff)) {
                        results.add(result);
                    }
                }
            } catch (Exception exception) {
                logger.warn("Could not search events of {}: {}", candidate, exception.toString());
            }
        }
        return results.stream()
                .sorted(Comparator.comparing(EventSearchResult::timestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(safeLimit)
                .toList();
    }

    @GetMapping("/search/snapshots")
    public ResponseEntity<String> snapshots(@RequestParam String application,
                                            @RequestParam String aggregateId,
                                            @RequestParam(defaultValue = "false") boolean withPayload) {
        return proxy(application, "/eventstore/aggregates/" + segment(aggregateId) + "/snapshots"
                + (withPayload ? "?withPayload=true" : ""));
    }

    @GetMapping("/scheduled-events")
    public ResponseEntity<String> scheduledEvents(@RequestParam String application,
                                                  @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return proxy(application, "/scheduled-events?limit=" + safeLimit);
    }

    private List<String> distinctApplications() {
        return instanceRepository.findAllByOrderByApplicationAscInstanceIdAsc()
                .stream()
                .map(instance -> instance.getApplication())
                .distinct()
                .sorted()
                .toList();
    }

    private static EventSearchResult toResult(String application, JsonNode node) {
        return new EventSearchResult(
                application,
                node.path("id").asText(null),
                node.path("aggregateId").asText(null),
                node.path("aggregateType").asText(null),
                node.path("sequenceNumber").asLong(0),
                node.path("payloadType").asText(null),
                node.path("payloadRevision").asText(null),
                node.path("globalIndex").asLong(0),
                parseInstant(node.path("timestamp").asText(null)));
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Instant cutoff(String within) {
        if (within == null || within.isBlank() || "all".equalsIgnoreCase(within)) {
            return null;
        }
        Duration window = switch (within.toLowerCase()) {
            case "1h" -> Duration.ofHours(1);
            case "6h" -> Duration.ofHours(6);
            case "24h" -> Duration.ofHours(24);
            default -> Duration.ofHours(24);
        };
        return Instant.now().minus(window);
    }

    private ResponseEntity<String> proxy(String application, String path) {
        String body = client.get(application, path);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null || body.isBlank() ? "[]" : body);
    }

    private static String segment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ contracts

    public record EventSearchResult(String application, String id, String aggregateId, String aggregateType,
                                    long sequenceNumber, String payloadType, String payloadRevision,
                                    long globalIndex, Instant timestamp) {
    }
}
