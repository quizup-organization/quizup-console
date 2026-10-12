package io.github.quizup.console.infrastructure.in.api;

import io.github.quizup.console.application.service.AuditService;
import io.github.quizup.console.domain.model.AuditEntry;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleDlqStatEntity;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleInstanceEntity;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleProcessorEntity;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleSegmentEntity;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleDlqStatJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleInstanceJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleProcessorJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleSegmentJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    public ConsoleQueryController(ConsoleInstanceJpaRepository instanceRepository,
                                  ConsoleProcessorJpaRepository processorRepository,
                                  ConsoleSegmentJpaRepository segmentRepository,
                                  ConsoleDlqStatJpaRepository dlqStatRepository,
                                  AuditService auditService) {
        this.instanceRepository = instanceRepository;
        this.processorRepository = processorRepository;
        this.segmentRepository = segmentRepository;
        this.dlqStatRepository = dlqStatRepository;
        this.auditService = auditService;
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

    // ------------------------------------------------------------------ mapping

    private static InstanceResponse toInstance(ConsoleInstanceEntity entity) {
        return new InstanceResponse(entity.getApplication(), entity.getInstanceId(), entity.getVersion(),
                entity.getUptimeSeconds(), entity.getFirstSeen(), entity.getLastSeen());
    }

    private static ProcessorResponse toProcessor(ConsoleProcessorEntity entity) {
        return new ProcessorResponse(entity.getApplication(), entity.getInstanceId(), entity.getName(),
                entity.getMode(), entity.isRunning(), entity.isError(), entity.isReplaying(),
                entity.isSupportsReset(), entity.getUpdatedAt());
    }

    private static SegmentResponse toSegment(ConsoleSegmentEntity entity) {
        return new SegmentResponse(entity.getSegmentId(), entity.getMask(), entity.isCaughtUp(),
                entity.isReplaying(), entity.isMerging(), entity.isErrorState(),
                entity.getCurrentPosition(), entity.getResetPosition(),
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

    public record InstanceResponse(String application, String instanceId, String version,
                                   long uptimeSeconds, Instant firstSeen, Instant lastSeen) {
    }

    public record ProcessorResponse(String application, String instanceId, String name, String mode,
                                    boolean running, boolean error, boolean replaying, boolean supportsReset,
                                    Instant updatedAt) {
    }

    public record ProcessorDetailResponse(ProcessorResponse processor, List<SegmentResponse> segments) {
    }

    public record SegmentResponse(int segmentId, int mask, boolean caughtUp, boolean replaying, boolean merging,
                                  boolean errorState, Long currentPosition, Long resetPosition,
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
}
