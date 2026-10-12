package io.github.quizup.console.infrastructure.in.api;

import io.github.quizup.console.application.service.AuditService;
import io.github.quizup.console.domain.model.AuditEntry;
import io.github.quizup.console.infrastructure.out.client.ServiceActionClient;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleDlqStatEntity;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleInstanceEntity;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleProcessorEntity;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleSegmentEntity;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleDlqStatJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleInstanceJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleProcessorJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleSegmentJpaRepository;
import io.github.quizup.microservice.security.SecurityHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * API de lecture de l'état courant (dernière ingestion d'agent) — réservée aux administrateurs.
 */
@RestController
@RequestMapping("/api/console")
@PreAuthorize("hasRole('ADMIN')")
public class ConsoleQueryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ConsoleInstanceJpaRepository instanceRepository;
    private final ConsoleProcessorJpaRepository processorRepository;
    private final ConsoleSegmentJpaRepository segmentRepository;
    private final ConsoleDlqStatJpaRepository dlqStatRepository;
    private final AuditService auditService;
    private final ServiceActionClient client;

    public ConsoleQueryController(ConsoleInstanceJpaRepository instanceRepository,
                                  ConsoleProcessorJpaRepository processorRepository,
                                  ConsoleSegmentJpaRepository segmentRepository,
                                  ConsoleDlqStatJpaRepository dlqStatRepository,
                                  AuditService auditService,
                                  ServiceActionClient client) {
        this.instanceRepository = instanceRepository;
        this.processorRepository = processorRepository;
        this.segmentRepository = segmentRepository;
        this.dlqStatRepository = dlqStatRepository;
        this.auditService = auditService;
        this.client = client;
    }

    @GetMapping("/applications")
    public List<InstanceResponse> applications() {
        return instanceRepository.findAllByOrderByApplicationAscInstanceIdAsc()
                .stream()
                .map(ConsoleQueryController::toInstance)
                .toList();
    }

    @GetMapping("/processors")
    public List<ProcessorResponse> processors(@RequestParam(required = false) String application) {
        List<ConsoleProcessorEntity> processors = application == null || application.isBlank()
                ? processorRepository.findAllByOrderByApplicationAscInstanceIdAscNameAsc()
                : processorRepository.findAllByApplicationOrderByInstanceIdAscNameAsc(application);
        return processors.stream().map(ConsoleQueryController::toProcessor).toList();
    }

    @GetMapping("/processors/{application}/{instanceId}/{name}")
    public ProcessorDetailResponse processor(@PathVariable String application,
                                             @PathVariable String instanceId,
                                             @PathVariable String name) {
        ConsoleProcessorEntity processor = processorRepository
                .findByApplicationAndInstanceIdAndName(application, instanceId, name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown processor: " + application + "/" + instanceId + "/" + name));
        List<SegmentResponse> segments = segmentRepository
                .findAllByApplicationAndInstanceIdAndProcessorOrderBySegmentIdAsc(application, instanceId, name)
                .stream()
                .map(ConsoleQueryController::toSegment)
                .toList();
        return new ProcessorDetailResponse(toProcessor(processor), segments);
    }

    @GetMapping("/dlq")
    public List<DlqResponse> dlq() {
        return dlqStatRepository.findAllByOrderByApplicationAscInstanceIdAscProcessorAsc()
                .stream()
                .map(ConsoleQueryController::toDlq)
                .toList();
    }

    @GetMapping("/audit")
    public AuditPageResponse audit(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Page<AuditEntry> result = auditService.find(PageRequest.of(Math.max(0, page), safeSize));
        return new AuditPageResponse(
                result.getContent().stream().map(ConsoleQueryController::toAudit).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    /**
     * Page « Utilisateurs » (contenu natif Axon Server) : pas de gestion locale de comptes (OIDC) —
     * administrateur courant + acteurs du journal d'audit.
     */
    @GetMapping("/users")
    public UsersResponse users() {
        return new UsersResponse(
                new CurrentUser(SecurityHelper.getUserId(), SecurityHelper.getUserEmail(), List.of("ROLE_ADMIN")),
                auditService.findActors());
    }

    /**
     * Lag d'un processor (position de queue vs positions) — relais vers le pod concerné
     * (adresse d'instance, repli Service).
     */
    @GetMapping("/processors/{application}/{instanceId}/{name}/lag")
    public ResponseEntity<String> processorLag(@PathVariable String application,
                                               @PathVariable String instanceId,
                                               @PathVariable String name) {
        String path = "/processors/" + UriUtils.encodePathSegment(name, StandardCharsets.UTF_8) + "/lag";
        String body = instanceRepository.findByApplicationAndInstanceId(application, instanceId)
                .map(instance -> instance.getAddress())
                .filter(address -> address != null && !address.isBlank())
                .map(address -> client.getAt(address, path))
                .orElseGet(() -> client.get(application, path));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null || body.isBlank() ? "{}" : body);
    }

    // ------------------------------------------------------------------ mapping

    private static InstanceResponse toInstance(ConsoleInstanceEntity entity) {
        return new InstanceResponse(entity.getApplication(), entity.getInstanceId(), entity.getVersion(),
                entity.getAddress(), entity.getUptimeSeconds(), entity.getFirstSeen(), entity.getLastSeen());
    }

    private static ProcessorResponse toProcessor(ConsoleProcessorEntity entity) {
        return new ProcessorResponse(entity.getApplication(), entity.getInstanceId(), entity.getName(),
                entity.getMode(), entity.isRunning(), entity.isError(), entity.isReplaying(),
                entity.isSupportsReset(), entity.isCanPause(), entity.isCanPlay(), entity.isCanSplit(),
                entity.isCanMerge(), entity.getTokenStoreIdentifier(), entity.getActiveThreads(),
                entity.getCapacity(), splitWarnings(entity.getWarnings()),
                entity.getUpdatedAt());
    }

    private static List<String> splitWarnings(String warnings) {
        return warnings == null || warnings.isBlank() ? List.of() : List.of(warnings.split("\n"));
    }

    private static SegmentResponse toSegment(ConsoleSegmentEntity entity) {
        return new SegmentResponse(entity.getSegmentId(), entity.getMask(), entity.isCaughtUp(),
                entity.isReplaying(), entity.isMerging(), entity.isErrorState(),
                entity.getCurrentPosition(), entity.getResetPosition(), entity.getOnePartOf(),
                entity.getErrorType(), entity.getErrorMessage());
    }

    private static DlqResponse toDlq(ConsoleDlqStatEntity entity) {
        return new DlqResponse(entity.getApplication(), entity.getInstanceId(), entity.getProcessor(),
                entity.getSize(), entity.getSequences(), entity.getUpdatedAt());
    }

    private static AuditResponse toAudit(AuditEntry entry) {
        return new AuditResponse(entry.occurredAt(), entry.actor(), entry.action(), entry.application(),
                entry.instanceId(), entry.target(), entry.parameters(), entry.result(), entry.error());
    }

    // ------------------------------------------------------------------ contracts

    public record InstanceResponse(String application, String instanceId, String version, String address,
                                   long uptimeSeconds, Instant firstSeen, Instant lastSeen) {
    }

    public record ProcessorResponse(String application, String instanceId, String name, String mode,
                                    boolean running, boolean error, boolean replaying, boolean supportsReset,
                                    boolean canPause, boolean canPlay, boolean canSplit, boolean canMerge,
                                    String tokenStoreIdentifier, int activeThreads, int capacity,
                                    List<String> warnings, Instant updatedAt) {
    }

    public record ProcessorDetailResponse(ProcessorResponse processor, List<SegmentResponse> segments) {
    }

    public record SegmentResponse(int segmentId, int mask, boolean caughtUp, boolean replaying, boolean merging,
                                  boolean errorState, Long currentPosition, Long resetPosition, int onePartOf,
                                  String errorType, String errorMessage) {
    }

    public record DlqResponse(String application, String instanceId, String processor,
                              long size, long sequences, Instant updatedAt) {
    }

    public record AuditResponse(Instant occurredAt, String actor,
                                io.github.quizup.console.domain.model.AuditAction action,
                                String application, String instanceId, String target, String parameters,
                                io.github.quizup.console.domain.model.AuditResult result, String error) {
    }

    public record AuditPageResponse(List<AuditResponse> items, int page, int size, long totalElements) {
    }

    public record UsersResponse(CurrentUser currentUser, List<AuditService.AuditActor> actors) {
    }

    public record CurrentUser(String userId, String email, List<String> roles) {
    }
}
