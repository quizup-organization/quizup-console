package io.github.quizup.console.infrastructure.in.api;

import io.github.quizup.console.application.service.IngestionService;
import io.github.quizup.console.infrastructure.in.api.request.SnapshotRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ingestion M2M des snapshots d'agent (client credentials {@code server-client}). Endpoint
 * <b>interne</b> : jamais ingressed.
 */
@RestController
@RequestMapping("/internal/ingest")
public class IngestionController {

    private static final Logger logger = LoggerFactory.getLogger(IngestionController.class);

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/snapshot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ingest(@RequestBody SnapshotRequest snapshot) {
        if (snapshot.version() == null || snapshot.version().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Snapshot version is required");
        }
        if (snapshot.application() == null || snapshot.application().isBlank()
                || snapshot.instanceId() == null || snapshot.instanceId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Snapshot application and instanceId are required");
        }
        ingestionService.ingest(snapshot);
    }
}
