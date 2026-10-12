package io.github.quizup.console.infrastructure.in.api;

import io.github.quizup.console.application.service.ConsoleHandlersService;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleInstanceEntity;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleProcessorEntity;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleSegmentEntity;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleInstanceJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleProcessorJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleSegmentJpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Détail d'une application (contenu natif Axon Server) : instances/réplicas, handlers
 * (commandes/queries), event processors et segments.
 */
@RestController
@RequestMapping("/api/console")
@PreAuthorize("hasRole('ADMIN')")
public class ConsoleApplicationController {

    private final ConsoleInstanceJpaRepository instanceRepository;
    private final ConsoleProcessorJpaRepository processorRepository;
    private final ConsoleSegmentJpaRepository segmentRepository;
    private final ConsoleHandlersService handlersService;

    public ConsoleApplicationController(ConsoleInstanceJpaRepository instanceRepository,
                                        ConsoleProcessorJpaRepository processorRepository,
                                        ConsoleSegmentJpaRepository segmentRepository,
                                        ConsoleHandlersService handlersService) {
        this.instanceRepository = instanceRepository;
        this.processorRepository = processorRepository;
        this.segmentRepository = segmentRepository;
        this.handlersService = handlersService;
    }

    @GetMapping("/applications/{application}")
    public ApplicationDetail application(@PathVariable String application) {
        List<ConsoleInstanceEntity> instances = instanceRepository.findAllByOrderByApplicationAscInstanceIdAsc()
                .stream()
                .filter(instance -> instance.getApplication().equals(application))
                .toList();
        if (instances.isEmpty() && processorRepository
                .findAllByApplicationOrderByInstanceIdAscNameAsc(application).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown application: " + application);
        }
        List<String> instanceIds = instances.stream().map(ConsoleInstanceEntity::getInstanceId).toList();

        List<ProcessorView> processors = processorRepository
                .findAllByApplicationOrderByInstanceIdAscNameAsc(application)
                .stream()
                .map(processor -> processorView(application, processor))
                .toList();

        return new ApplicationDetail(
                application,
                instances.stream().map(ConsoleApplicationController::instanceView).toList(),
                handlersService.handlers(application, instanceIds),
                processors);
    }

    private ProcessorView processorView(String application, ConsoleProcessorEntity processor) {
        List<SegmentView> segments = segmentRepository
                .findAllByApplicationAndInstanceIdAndProcessorOrderBySegmentIdAsc(
                        application, processor.getInstanceId(), processor.getName())
                .stream()
                .map(ConsoleApplicationController::segmentView)
                .toList();
        return new ProcessorView(processor.getApplication(), processor.getInstanceId(), processor.getName(),
                processor.getMode(), processor.isRunning(), processor.isError(), processor.isReplaying(),
                processor.isCanPause(), processor.isCanPlay(), processor.isCanSplit(), processor.isCanMerge(),
                processor.getTokenStoreIdentifier(), processor.getActiveThreads(), processor.getCapacity(),
                splitWarnings(processor.getWarnings()), processor.getUpdatedAt(), segments);
    }

    private static List<String> splitWarnings(String warnings) {
        return warnings == null || warnings.isBlank() ? List.of() : List.of(warnings.split("\n"));
    }

    private static InstanceView instanceView(ConsoleInstanceEntity entity) {
        return new InstanceView(entity.getApplication(), entity.getInstanceId(), entity.getVersion(),
                entity.getAddress(), entity.getUptimeSeconds(), entity.getFirstSeen(), entity.getLastSeen());
    }

    private static SegmentView segmentView(ConsoleSegmentEntity entity) {
        return new SegmentView(entity.getSegmentId(), entity.getMask(), entity.isCaughtUp(),
                entity.isReplaying(), entity.isMerging(), entity.isErrorState(),
                entity.getCurrentPosition(), entity.getResetPosition(), entity.getOnePartOf(),
                entity.getErrorType(), entity.getErrorMessage());
    }

    // ------------------------------------------------------------------ contracts

    public record ApplicationDetail(String application,
                                    List<InstanceView> instances,
                                    ConsoleHandlersService.Handlers handlers,
                                    List<ProcessorView> processors) {
    }

    public record InstanceView(String application, String instanceId, String version, String address,
                               long uptimeSeconds, Instant firstSeen, Instant lastSeen) {
    }

    public record ProcessorView(String application, String instanceId, String name, String mode,
                                boolean running, boolean error, boolean replaying,
                                boolean canPause, boolean canPlay, boolean canSplit, boolean canMerge,
                                String tokenStoreIdentifier, int activeThreads, int capacity,
                                List<String> warnings, Instant updatedAt, List<SegmentView> segments) {
    }

    public record SegmentView(int segmentId, int mask, boolean caughtUp, boolean replaying, boolean merging,
                              boolean errorState, Long currentPosition, Long resetPosition, int onePartOf,
                              String errorType, String errorMessage) {
    }
}
