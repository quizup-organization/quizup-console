package io.github.quizup.console.application.service;

import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleInstanceEntity;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleDlqStatJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleInstanceJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleProcessorJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleSegmentJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsoleCleanupServiceTest {

    private final ConsoleInstanceJpaRepository instanceRepository = mock(ConsoleInstanceJpaRepository.class);
    private final ConsoleProcessorJpaRepository processorRepository = mock(ConsoleProcessorJpaRepository.class);
    private final ConsoleSegmentJpaRepository segmentRepository = mock(ConsoleSegmentJpaRepository.class);
    private final ConsoleDlqStatJpaRepository dlqStatRepository = mock(ConsoleDlqStatJpaRepository.class);

    private final ConsoleCleanupService service =
            new ConsoleCleanupService(instanceRepository, processorRepository, segmentRepository, dlqStatRepository);

    @Test
    void purgesStaleInstancesAndChildren() {
        ConsoleInstanceEntity stale = new ConsoleInstanceEntity();
        stale.setApplication("quizup-game");
        stale.setInstanceId("quizup-game-old-1");
        when(instanceRepository.findAllByLastSeenBefore(any(Instant.class))).thenReturn(List.of(stale));

        service.purgeStaleInstances();

        verify(processorRepository).deleteByApplicationAndInstanceId("quizup-game", "quizup-game-old-1");
        verify(segmentRepository).deleteByApplicationAndInstanceId("quizup-game", "quizup-game-old-1");
        verify(dlqStatRepository).deleteByApplicationAndInstanceId("quizup-game", "quizup-game-old-1");
        verify(instanceRepository).delete(stale);
    }

    @Test
    void doesNothingWhenNoStaleInstance() {
        when(instanceRepository.findAllByLastSeenBefore(any(Instant.class))).thenReturn(List.of());

        service.purgeStaleInstances();

        verify(instanceRepository, never()).delete(any(ConsoleInstanceEntity.class));
    }
}
