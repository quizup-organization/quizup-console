package io.github.quizup.console.infrastructure.in.api;

import io.github.quizup.console.application.service.AuditService;
import io.github.quizup.console.domain.model.AuditAction;
import io.github.quizup.console.domain.model.AuditEntry;
import io.github.quizup.console.domain.model.AuditResult;
import io.github.quizup.console.infrastructure.out.client.ServiceActionClient;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleInstanceEntity;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleInstanceJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleProcessorJpaRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Actions d'exploitation (proxy M2M vers l'API interne du service ciblé) — réservées aux
 * administrateurs et <b>toutes auditées</b>.
 *
 * <p>Les actions par instance sont envoyées à l'<b>adresse du pod</b> (snapshot agent 4.7) quand
 * elle est connue, sinon à l'URL du Service (repli mono-réplica). Les actions « toutes instances »
 * itèrent tous les pods hébergeant le processor.</p>
 */
@RestController
@RequestMapping("/api/console/actions")
@PreAuthorize("hasRole('ADMIN')")
public class ConsoleActionController {

    private static final String JSON = "{";

    private final ServiceActionClient client;
    private final AuditService auditService;
    private final ConsoleInstanceJpaRepository instanceRepository;
    private final ConsoleProcessorJpaRepository processorRepository;

    public ConsoleActionController(ServiceActionClient client,
                                   AuditService auditService,
                                   ConsoleInstanceJpaRepository instanceRepository,
                                   ConsoleProcessorJpaRepository processorRepository) {
        this.client = client;
        this.auditService = auditService;
        this.instanceRepository = instanceRepository;
        this.processorRepository = processorRepository;
    }

    @PostMapping("/dlq/{application}/{instanceId}/{processor}/sequences/{sequenceId}/retry")
    public ResponseEntity<String> retryDlqSequence(@PathVariable String application,
                                                   @PathVariable String instanceId,
                                                   @PathVariable String processor,
                                                   @PathVariable String sequenceId) {
        String path = "/dlq/" + segment(processor) + "/sequences/" + segment(sequenceId) + "/retry";
        return execute(AuditAction.RETRY_DLQ_SEQUENCE, application, instanceId,
                processor + "#" + sequenceId, null, () -> postToInstance(application, instanceId, path));
    }

    @DeleteMapping("/dlq/{application}/{instanceId}/{processor}/sequences/{sequenceId}")
    public ResponseEntity<String> deleteDlqSequence(@PathVariable String application,
                                                    @PathVariable String instanceId,
                                                    @PathVariable String processor,
                                                    @PathVariable String sequenceId) {
        String path = "/dlq/" + segment(processor) + "/sequences/" + segment(sequenceId);
        return execute(AuditAction.DELETE_DLQ_SEQUENCE, application, instanceId,
                processor + "#" + sequenceId, null, () -> deleteToInstance(application, instanceId, path));
    }

    @PostMapping("/dlq/{application}/{instanceId}/{processor}/clear")
    public ResponseEntity<String> clearDlq(@PathVariable String application,
                                           @PathVariable String instanceId,
                                           @PathVariable String processor) {
        String path = "/dlq/" + segment(processor) + "/clear";
        return execute(AuditAction.CLEAR_DLQ, application, instanceId, processor, null,
                () -> postToInstance(application, instanceId, path));
    }

    @PostMapping("/processors/{application}/{instanceId}/{name}/pause")
    public ResponseEntity<String> pauseProcessor(@PathVariable String application,
                                                 @PathVariable String instanceId,
                                                 @PathVariable String name) {
        String path = "/processors/" + segment(name) + "/pause";
        return execute(AuditAction.PAUSE_PROCESSOR, application, instanceId, name, null,
                () -> postToInstance(application, instanceId, path));
    }

    @PostMapping("/processors/{application}/{instanceId}/{name}/start")
    public ResponseEntity<String> startProcessor(@PathVariable String application,
                                                 @PathVariable String instanceId,
                                                 @PathVariable String name) {
        String path = "/processors/" + segment(name) + "/start";
        return execute(AuditAction.START_PROCESSOR, application, instanceId, name, null,
                () -> postToInstance(application, instanceId, path));
    }

    @PostMapping("/processors/{application}/{instanceId}/{name}/reset")
    public ResponseEntity<String> resetProcessor(@PathVariable String application,
                                                 @PathVariable String instanceId,
                                                 @PathVariable String name,
                                                 @RequestBody ResetRequest request) {
        String path = "/processors/" + segment(name) + "/reset";
        String parameters = resetParameters(request);
        return execute(AuditAction.RESET_PROCESSOR, application, instanceId, name, parameters,
                () -> postToInstance(application, instanceId, path, request));
    }

    @PostMapping("/processors/{application}/{instanceId}/{name}/segments/{segmentId}/split")
    public ResponseEntity<String> splitSegment(@PathVariable String application,
                                               @PathVariable String instanceId,
                                               @PathVariable String name,
                                               @PathVariable int segmentId) {
        String path = "/processors/" + segment(name) + "/segments/" + segmentId + "/split";
        return execute(AuditAction.SPLIT_SEGMENT, application, instanceId,
                name + "#" + segmentId, null, () -> postToInstance(application, instanceId, path));
    }

    @PostMapping("/processors/{application}/{instanceId}/{name}/segments/{segmentId}/merge")
    public ResponseEntity<String> mergeSegment(@PathVariable String application,
                                               @PathVariable String instanceId,
                                               @PathVariable String name,
                                               @PathVariable int segmentId) {
        String path = "/processors/" + segment(name) + "/segments/" + segmentId + "/merge";
        return execute(AuditAction.MERGE_SEGMENT, application, instanceId,
                name + "#" + segmentId, null, () -> postToInstance(application, instanceId, path));
    }

    // ------------------------------------------------------------------ toutes instances

    @PostMapping("/processors/{application}/{name}/pause-all")
    public AllInstancesResponse pauseAll(@PathVariable String application, @PathVariable String name) {
        return executeAll(AuditAction.PAUSE_PROCESSOR, application, name, "pause");
    }

    @PostMapping("/processors/{application}/{name}/start-all")
    public AllInstancesResponse startAll(@PathVariable String application, @PathVariable String name) {
        return executeAll(AuditAction.START_PROCESSOR, application, name, "start");
    }

    // ------------------------------------------------------------------ scheduled events

    @DeleteMapping("/scheduled-events/{application}/{taskName}/{taskInstance}")
    public ResponseEntity<String> cancelScheduledEvent(@PathVariable String application,
                                                       @PathVariable String taskName,
                                                       @PathVariable String taskInstance) {
        String path = "/scheduled-events/" + segment(taskName) + "/" + segment(taskInstance);
        return execute(AuditAction.CANCEL_SCHEDULED_EVENT, application, null,
                taskName + "#" + taskInstance, null,
                () -> deleteOnAnyInstance(application, path));
    }

    // ------------------------------------------------------------------ helpers

    private AllInstancesResponse executeAll(AuditAction action, String application, String name, String operation) {
        List<String> instanceIds = processorRepository
                .findAllByApplicationOrderByInstanceIdAscNameAsc(application)
                .stream()
                .filter(processor -> processor.getName().equals(name))
                .map(processor -> processor.getInstanceId())
                .distinct()
                .toList();
        List<String> failures = new ArrayList<>();
        int succeeded = 0;
        String path = "/processors/" + segment(name) + "/" + operation;
        for (String instanceId : instanceIds) {
            try {
                postToInstance(application, instanceId, path);
                succeeded++;
            } catch (RuntimeException exception) {
                failures.add(instanceId + ": " + exception.getMessage());
            }
        }
        auditService.record(new AuditEntry(Instant.now(), SecurityHelper.getUserEmail(), action,
                application, null, name + "#all", "instances=" + instanceIds.size(),
                failures.isEmpty() ? AuditResult.SUCCESS : AuditResult.FAILURE,
                failures.isEmpty() ? null : truncate(String.join("; ", failures))));
        return new AllInstancesResponse(instanceIds.size(), succeeded, failures);
    }

    private String postToInstance(String application, String instanceId, String path) {
        return postToInstance(application, instanceId, path, null);
    }

    private String postToInstance(String application, String instanceId, String path, Object body) {
        ConsoleInstanceEntity instance = instanceRepository
                .findByApplicationAndInstanceId(application, instanceId)
                .orElse(null);
        if (instance != null && instance.getAddress() != null && !instance.getAddress().isBlank()) {
            return body == null
                    ? client.postAt(instance.getAddress(), path)
                    : client.postAt(instance.getAddress(), path, body);
        }
        return body == null ? client.post(application, path) : client.post(application, path, body);
    }

    private String deleteToInstance(String application, String instanceId, String path) {
        ConsoleInstanceEntity instance = instanceRepository
                .findByApplicationAndInstanceId(application, instanceId)
                .orElse(null);
        if (instance != null && instance.getAddress() != null && !instance.getAddress().isBlank()) {
            return client.deleteAt(instance.getAddress(), path);
        }
        return client.delete(application, path);
    }

    private String deleteOnAnyInstance(String application, String path) {
        return instanceRepository.findAllByOrderByApplicationAscInstanceIdAsc()
                .stream()
                .filter(instance -> instance.getApplication().equals(application))
                .findFirst()
                .map(instance -> instance.getAddress() != null && !instance.getAddress().isBlank()
                        ? client.deleteAt(instance.getAddress(), path)
                        : client.delete(application, path))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Unknown application: " + application));
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

    public record AllInstancesResponse(int instances, int succeeded, List<String> failures) {
    }
}
