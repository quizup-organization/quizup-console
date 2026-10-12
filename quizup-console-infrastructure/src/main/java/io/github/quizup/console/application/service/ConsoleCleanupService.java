package io.github.quizup.console.application.service;

import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleInstanceEntity;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleDlqStatJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleInstanceJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleProcessorJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleSegmentJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Purge les instances dont l'agent ne pousse plus (pod remplacé/arrêté) et leurs lignes filles :
 * sans cela, les anciens réplicas resteraient affichés « hors ligne » indéfiniment.
 */
@Service
public class ConsoleCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleCleanupService.class);
    private static final Duration STALE_AFTER = Duration.ofMinutes(10);
    private static final long CLEANUP_INTERVAL_MS = 300_000;

    private final ConsoleInstanceJpaRepository instanceRepository;
    private final ConsoleProcessorJpaRepository processorRepository;
    private final ConsoleSegmentJpaRepository segmentRepository;
    private final ConsoleDlqStatJpaRepository dlqStatRepository;

    public ConsoleCleanupService(ConsoleInstanceJpaRepository instanceRepository,
                                 ConsoleProcessorJpaRepository processorRepository,
                                 ConsoleSegmentJpaRepository segmentRepository,
                                 ConsoleDlqStatJpaRepository dlqStatRepository) {
        this.instanceRepository = instanceRepository;
        this.processorRepository = processorRepository;
        this.segmentRepository = segmentRepository;
        this.dlqStatRepository = dlqStatRepository;
    }

    @Scheduled(fixedDelay = CLEANUP_INTERVAL_MS, initialDelay = CLEANUP_INTERVAL_MS)
    @Transactional
    public void purgeStaleInstances() {
        Instant cutoff = Instant.now().minus(STALE_AFTER);
        List<ConsoleInstanceEntity> stale = instanceRepository.findAllByLastSeenBefore(cutoff);
        for (ConsoleInstanceEntity instance : stale) {
            processorRepository.deleteByApplicationAndInstanceId(instance.getApplication(), instance.getInstanceId());
            segmentRepository.deleteByApplicationAndInstanceId(instance.getApplication(), instance.getInstanceId());
            dlqStatRepository.deleteByApplicationAndInstanceId(instance.getApplication(), instance.getInstanceId());
            instanceRepository.delete(instance);
        }
        if (!stale.isEmpty()) {
            logger.info("Purged {} stale console instance(s) (last seen before {})", stale.size(), cutoff);
        }
    }
}
