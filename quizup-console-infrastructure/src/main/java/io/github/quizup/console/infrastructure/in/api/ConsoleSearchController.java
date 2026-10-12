package io.github.quizup.console.infrastructure.in.api;

import io.github.quizup.console.infrastructure.out.client.ServiceActionClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * Recherche (contenu natif Axon Server) : événements par agrégat, recherche par type, snapshots,
 * et scheduled events (deadlines). Proxys M2M vers l'API interne des services — réponse JSON
 * relayée telle quelle.
 */
@RestController
@RequestMapping("/api/console")
@PreAuthorize("hasRole('ADMIN')")
public class ConsoleSearchController {

    private static final int MAX_LIMIT = 200;

    private final ServiceActionClient client;

    public ConsoleSearchController(ServiceActionClient client) {
        this.client = client;
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
    public ResponseEntity<String> eventsByType(@RequestParam String application,
                                               @RequestParam(required = false) String type,
                                               @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        String path = "/eventstore/events?limit=" + safeLimit
                + (type == null || type.isBlank() ? "" : "&type=" + UriUtils.encodeQueryParam(type, StandardCharsets.UTF_8));
        return proxy(application, path);
    }

    @GetMapping("/search/snapshots")
    public ResponseEntity<String> snapshots(@RequestParam String application,
                                            @RequestParam String aggregateId) {
        return proxy(application, "/eventstore/aggregates/" + segment(aggregateId) + "/snapshots");
    }

    @GetMapping("/scheduled-events")
    public ResponseEntity<String> scheduledEvents(@RequestParam String application,
                                                  @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return proxy(application, "/scheduled-events?limit=" + safeLimit);
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
}
