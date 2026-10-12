package io.github.quizup.console.application.service;

import io.github.quizup.console.infrastructure.in.api.request.SnapshotRequest;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleDlqStatEntity;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleInstanceEntity;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleProcessorEntity;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleSegmentEntity;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleDlqStatJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleInstanceJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleProcessorJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleSegmentJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Ingestion d'un snapshot d'agent : met à jour l'état courant de l'instance (upsert) et remplace
 * ses processors, segments et stats DLQ. Le remplacement (delete + insert) garantit la disparition
 * des processors/segments supprimés côté application, pour un volume borné (une instance a quelques
 * dizaines de lignes).
 */
@Service
public class IngestionService {

    private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);

    private final ConsoleInstanceJpaRepository instanceRepository;
    private final ConsoleProcessorJpaRepository processorRepository;
    private final ConsoleSegmentJpaRepository segmentRepository;
    private final ConsoleDlqStatJpaRepository dlqStatRepository;

    public IngestionService(ConsoleInstanceJpaRepository instanceRepository,
                            ConsoleProcessorJpaRepository processorRepository,
                            ConsoleSegmentJpaRepository segmentRepository,
                            ConsoleDlqStatJpaRepository dlqStatRepository) {
        this.instanceRepository = instanceRepository;
        this.processorRepository = processorRepository;
        this.segmentRepository = segmentRepository;
        this.dlqStatRepository = dlqStatRepository;
    }

    @Transactional
    public void ingest(SnapshotRequest snapshot) {
        Instant now = Instant.now();
        String application = snapshot.application();
        String instanceId = snapshot.instanceId();

        upsertInstance(snapshot, application, instanceId, now);

        processorRepository.deleteByApplicationAndInstanceId(application, instanceId);
        segmentRepository.deleteByApplicationAndInstanceId(application, instanceId);
        dlqStatRepository.deleteByApplicationAndInstanceId(application, instanceId);

        List<SnapshotRequest.ProcessorRequest> processors =
                snapshot.processors() == null ? List.of() : snapshot.processors();
        for (SnapshotRequest.ProcessorRequest processor : processors) {
            processorRepository.save(processorEntity(application, instanceId, processor, now));
            if (processor.segments() != null) {
                for (SnapshotRequest.SegmentRequest segment : processor.segments()) {
                    segmentRepository.save(segmentEntity(application, instanceId, processor, segment));
                }
            }
            if (processor.dlq() != null) {
                dlqStatRepository.save(dlqEntity(application, instanceId, processor, now));
            }
        }
        logger.debug("Ingested snapshot of {} / {} ({} processors)", application, instanceId, processors.size());
    }

    private void upsertInstance(SnapshotRequest snapshot, String application, String instanceId, Instant now) {
        ConsoleInstanceEntity instance = instanceRepository
                .findByApplicationAndInstanceId(application, instanceId)
                .orElseGet(() -> {
                    ConsoleInstanceEntity created = new ConsoleInstanceEntity();
                    created.setApplication(application);
                    created.setInstanceId(instanceId);
                    created.setFirstSeen(now);
                    return created;
                });
        instance.setVersion(snapshot.applicationVersion());
        instance.setAddress(snapshot.address());
        instance.setUptimeSeconds(snapshot.uptimeSeconds());
        instance.setLastSeen(now);
        instanceRepository.save(instance);
    }

    private static ConsoleProcessorEntity processorEntity(String application, String instanceId,
                                                          SnapshotRequest.ProcessorRequest processor,
                                                          Instant now) {
        ConsoleProcessorEntity entity = new ConsoleProcessorEntity();
        entity.setApplication(application);
        entity.setInstanceId(instanceId);
        entity.setName(processor.name());
        entity.setMode(processor.mode());
        entity.setRunning(processor.running());
        entity.setError(processor.error());
        entity.setReplaying(processor.replaying());
        entity.setSupportsReset(processor.supportsReset());
        entity.setCanPause(processor.canPause());
        entity.setCanPlay(processor.canPlay());
        entity.setCanSplit(processor.canSplit());
        entity.setCanMerge(processor.canMerge());
        entity.setTokenStoreIdentifier(processor.tokenStoreIdentifier());
        entity.setActiveThreads(processor.activeThreads());
        entity.setCapacity(processor.capacity());
        entity.setWarnings(joinWarnings(processor.warnings()));
        entity.setUpdatedAt(now);
        return entity;
    }

    private static String joinWarnings(List<String> warnings) {
        return warnings == null || warnings.isEmpty() ? null : String.join("\n", warnings);
    }

    private static ConsoleSegmentEntity segmentEntity(String application, String instanceId,
                                                      SnapshotRequest.ProcessorRequest processor,
                                                      SnapshotRequest.SegmentRequest segment) {
        ConsoleSegmentEntity entity = new ConsoleSegmentEntity();
        entity.setApplication(application);
        entity.setInstanceId(instanceId);
        entity.setProcessor(processor.name());
        entity.setSegmentId(segment.segmentId());
        entity.setMask(segment.mask());
        entity.setCaughtUp(segment.caughtUp());
        entity.setReplaying(segment.replaying());
        entity.setMerging(segment.merging());
        entity.setErrorState(segment.errorState());
        entity.setCurrentPosition(segment.currentPosition());
        entity.setResetPosition(segment.resetPosition());
        entity.setOnePartOf(segment.onePartOf());
        entity.setErrorType(segment.errorType());
        entity.setErrorMessage(segment.errorMessage());
        return entity;
    }

    private static ConsoleDlqStatEntity dlqEntity(String application, String instanceId,
                                                  SnapshotRequest.ProcessorRequest processor,
                                                  Instant now) {
        ConsoleDlqStatEntity entity = new ConsoleDlqStatEntity();
        entity.setApplication(application);
        entity.setInstanceId(instanceId);
        entity.setProcessor(processor.name());
        entity.setSize(processor.dlq().size());
        entity.setSequences(processor.dlq().sequences());
        entity.setUpdatedAt(now);
        return entity;
    }
}
