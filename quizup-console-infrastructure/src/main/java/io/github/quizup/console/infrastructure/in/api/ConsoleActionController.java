package io.github.quizup.console.infrastructure.in.api;

import io.github.quizup.console.application.service.AuditService;
import io.github.quizup.console.domain.model.AuditAction;
import io.github.quizup.console.domain.model.AuditEntry;
import io.github.quizup.console.domain.model.AuditResult;
import io.github.quizup.console.infrastructure.out.client.ServiceActionClient;
import io.github.quizup.microservice.security.SecurityHelper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Actions d'exploitation (proxy M2M vers l'API interne du service ciblé) — réservées aux
 * administrateurs et <b>toutes auditées</b>.
 *
 * <p>Le <b>reset</b> exige un processor à l'arrêt : la console (ou le web-console) orchestre
 * pause → reset → start sur toutes les instances du processor.</p>
 */
@RestController
@RequestMapping("/api/console/actions")
@PreAuthorize("hasRole('ADMIN')")
public class ConsoleActionController {

    private static final String JSON = "{";

    private final ServiceActionClient client;
    private final AuditService auditService;

    public ConsoleActionController(ServiceActionClient client, AuditService auditService) {
        this.client = client;
        this.auditService = auditService;
    }

    @PostMapping("/dlq/{application}/{instanceId}/{processor}/sequences/{sequenceId}/retry")
    public ResponseEntity<String> retryDlqSequence(@PathVariable String application,
                                                   @PathVariable String instanceId,
                                                   @PathVariable String processor,
                                                   @PathVariable String sequenceId) {
        String path = "/dlq/" + segment(processor) + "/sequences/" + segment(sequenceId) + "/retry";
        return execute(AuditAction.RETRY_DLQ_SEQUENCE, application, instanceId,
                processor + "#" + sequenceId, null, () -> client.post(application, path));
    }

    @DeleteMapping("/dlq/{application}/{instanceId}/{processor}/sequences/{sequenceId}")
    public ResponseEntity<String> deleteDlqSequence(@PathVariable String application,
                                                    @PathVariable String instanceId,
                                                    @PathVariable String processor,
                                                    @PathVariable String sequenceId) {
        String path = "/dlq/" + segment(processor) + "/sequences/" + segment(sequenceId);
        return execute(AuditAction.DELETE_DLQ_SEQUENCE, application, instanceId,
                processor + "#" + sequenceId, null, () -> client.delete(application, path));
    }

    @PostMapping("/dlq/{application}/{instanceId}/{processor}/clear")
    public ResponseEntity<String> clearDlq(@PathVariable String application,
                                           @PathVariable String instanceId,
                                           @PathVariable String processor) {
        String path = "/dlq/" + segment(processor) + "/clear";
        return execute(AuditAction.CLEAR_DLQ, application, instanceId, processor, null,
                () -> client.post(application, path));
    }

    @PostMapping("/processors/{application}/{instanceId}/{name}/pause")
    public ResponseEntity<String> pauseProcessor(@PathVariable String application,
                                                 @PathVariable String instanceId,
                                                 @PathVariable String name) {
        String path = "/processors/" + segment(name) + "/pause";
        return execute(AuditAction.PAUSE_PROCESSOR, application, instanceId, name, null,
                () -> client.post(application, path));
    }

    @PostMapping("/processors/{application}/{instanceId}/{name}/start")
    public ResponseEntity<String> startProcessor(@PathVariable String application,
                                                 @PathVariable String instanceId,
                                                 @PathVariable String name) {
        String path = "/processors/" + segment(name) + "/start";
        return execute(AuditAction.START_PROCESSOR, application, instanceId, name, null,
                () -> client.post(application, path));
    }

    @PostMapping("/processors/{application}/{instanceId}/{name}/reset")
    public ResponseEntity<String> resetProcessor(@PathVariable String application,
                                                 @PathVariable String instanceId,
                                                 @PathVariable String name,
                                                 @RequestBody ResetRequest request) {
        String path = "/processors/" + segment(name) + "/reset";
        String parameters = resetParameters(request);
        return execute(AuditAction.RESET_PROCESSOR, application, instanceId, name, parameters,
                () -> client.post(application, path, request));
    }

    @PostMapping("/processors/{application}/{instanceId}/{name}/segments/{segmentId}/split")
    public ResponseEntity<String> splitSegment(@PathVariable String application,
                                               @PathVariable String instanceId,
                                               @PathVariable String name,
                                               @PathVariable int segmentId) {
        String path = "/processors/" + segment(name) + "/segments/" + segmentId + "/split";
        return execute(AuditAction.SPLIT_SEGMENT, application, instanceId,
                name + "#" + segmentId, null, () -> client.post(application, path));
    }

    @PostMapping("/processors/{application}/{instanceId}/{name}/segments/{segmentId}/merge")
    public ResponseEntity<String> mergeSegment(@PathVariable String application,
                                               @PathVariable String instanceId,
                                               @PathVariable String name,
                                               @PathVariable int segmentId) {
        String path = "/processors/" + segment(name) + "/segments/" + segmentId + "/merge";
        return execute(AuditAction.MERGE_SEGMENT, application, instanceId,
                name + "#" + segmentId, null, () -> client.post(application, path));
    }

    private ResponseEntity<String> execute(AuditAction action, String application, String instanceId,
                                           String target, String parameters, Supplier<String> call) {
        try {
            String body = call.get();
            audit(action, application, instanceId, target, parameters, AuditResult.SUCCESS, null);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body == null || body.isBlank() ? JSON + "}" : body);
        } catch (RuntimeException exception) {
            audit(action, application, instanceId, target, parameters, AuditResult.FAILURE,
                    truncate(exception.getMessage()));
            throw exception;
        }
    }

    private void audit(AuditAction action, String application, String instanceId, String target,
                       String parameters, AuditResult result, String error) {
        auditService.record(new AuditEntry(Instant.now(), SecurityHelper.getUserEmail(), action,
                application, instanceId, target, parameters, result, error));
    }

    private static String resetParameters(ResetRequest request) {
        if (request == null) {
            return null;
        }
        return request.position() + (request.instant() == null ? "" : "@" + request.instant());
    }

    private static String segment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 1024) {
            return value;
        }
        return value.substring(0, 1024) + "…";
    }

    // ------------------------------------------------------------------ contracts

    public record ResetRequest(String position, Instant instant) {
    }
}
