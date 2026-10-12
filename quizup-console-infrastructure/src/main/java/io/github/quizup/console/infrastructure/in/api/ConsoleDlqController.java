package io.github.quizup.console.infrastructure.in.api;

import io.github.quizup.console.infrastructure.out.client.ServiceActionClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * Lecture proxy des séquences DLQ d'un processor : relaie la réponse paginée de l'API interne du
 * service ciblé ({@code /internal/console/dlq/{group}}). Réservé aux administrateurs.
 */
@RestController
@RequestMapping("/api/console/dlq")
@PreAuthorize("hasRole('ADMIN')")
public class ConsoleDlqController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ServiceActionClient client;

    public ConsoleDlqController(ServiceActionClient client) {
        this.client = client;
    }

    @GetMapping("/{application}/{instanceId}/{processor}/sequences")
    public ResponseEntity<String> sequences(@PathVariable String application,
                                            @PathVariable String instanceId,
                                            @PathVariable String processor,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        String path = "/dlq/" + UriUtils.encodePathSegment(processor, StandardCharsets.UTF_8)
                + "?page=" + safePage + "&size=" + safeSize;
        String body = client.get(application, path);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null || body.isBlank() ? "{}" : body);
    }
}
