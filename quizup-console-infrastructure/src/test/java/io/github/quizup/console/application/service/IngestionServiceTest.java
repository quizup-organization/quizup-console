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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionServiceTest {

    private final ConsoleInstanceJpaRepository instanceRepository = mock(ConsoleInstanceJpaRepository.class);
    private final ConsoleProcessorJpaRepository processorRepository = mock(ConsoleProcessorJpaRepository.class);
    private final ConsoleSegmentJpaRepository segmentRepository = mock(ConsoleSegmentJpaRepository.class);
    private final ConsoleDlqStatJpaRepository dlqStatRepository = mock(ConsoleDlqStatJpaRepository.class);

    private final IngestionService service =
            new IngestionService(instanceRepository, processorRepository, segmentRepository, dlqStatRepository);

    @Test
    void createsInstanceOnFirstSnapshotAndReplacesChildren() {
        when(instanceRepository.findByApplicationAndInstanceId("quizup-game", "pod-1"))
                .thenReturn(Optional.empty());

        service.ingest(snapshot());

        ArgumentCaptor<ConsoleInstanceEntity> instanceCaptor = ArgumentCaptor.forClass(ConsoleInstanceEntity.class);
        verify(instanceRepository).save(instanceCaptor.capture());
        ConsoleInstanceEntity instance = instanceCaptor.getValue();
        assertThat(instance.getApplication()).isEqualTo("quizup-game");
        assertThat(instance.getInstanceId()).isEqualTo("pod-1");
        assertThat(instance.getVersion()).isEqualTo("8.0.1");
        assertThat(instance.getUptimeSeconds()).isEqualTo(120);
        assertThat(instance.getFirstSeen()).isEqualTo(instance.getLastSeen());

        verify(processorRepository).deleteByApplicationAndInstanceId("quizup-game", "pod-1");
        verify(segmentRepository).deleteByApplicationAndInstanceId("quizup-game", "pod-1");
        verify(dlqStatRepository).deleteByApplicationAndInstanceId("quizup-game", "pod-1");

        ArgumentCaptor<ConsoleProcessorEntity> processorCaptor =
                ArgumentCaptor.forClass(ConsoleProcessorEntity.class);
        verify(processorRepository).save(processorCaptor.capture());
        assertThat(processorCaptor.getValue().getName()).isEqualTo("game-projection");
        assertThat(processorCaptor.getValue().isReplaying()).isTrue();
        assertThat(processorCaptor.getValue().isCanPause()).isTrue();
        assertThat(processorCaptor.getValue().getTokenStoreIdentifier()).isEqualTo("test-token-store");
        assertThat(processorCaptor.getValue().getWarnings()).isEqualTo("1 segment(s) en erreur");

        ArgumentCaptor<ConsoleSegmentEntity> segmentCaptor =
                ArgumentCaptor.forClass(ConsoleSegmentEntity.class);
        verify(segmentRepository).save(segmentCaptor.capture());
        assertThat(segmentCaptor.getValue().getCurrentPosition()).isEqualTo(42L);
        assertThat(segmentCaptor.getValue().getOnePartOf()).isEqualTo(1);

        ArgumentCaptor<ConsoleDlqStatEntity> dlqCaptor = ArgumentCaptor.forClass(ConsoleDlqStatEntity.class);
        verify(dlqStatRepository).save(dlqCaptor.capture());
        assertThat(dlqCaptor.getValue().getSize()).isEqualTo(2);
        assertThat(dlqCaptor.getValue().getSequences()).isEqualTo(1);
    }

    @Test
    void keepsFirstSeenOnSubsequentSnapshots() {
        Instant firstSeen = Instant.now().minusSeconds(3600);
        ConsoleInstanceEntity existing = new ConsoleInstanceEntity();
        existing.setApplication("quizup-game");
        existing.setInstanceId("pod-1");
        existing.setFirstSeen(firstSeen);
        existing.setLastSeen(firstSeen);
        when(instanceRepository.findByApplicationAndInstanceId("quizup-game", "pod-1"))
                .thenReturn(Optional.of(existing));

        service.ingest(snapshot());

        ArgumentCaptor<ConsoleInstanceEntity> instanceCaptor = ArgumentCaptor.forClass(ConsoleInstanceEntity.class);
        verify(instanceRepository).save(instanceCaptor.capture());
        assertThat(instanceCaptor.getValue().getFirstSeen()).isEqualTo(firstSeen);
        assertThat(instanceCaptor.getValue().getLastSeen()).isAfter(firstSeen);
    }

    private static SnapshotRequest snapshot() {
        return new SnapshotRequest(
                "v1",
                Instant.now(),
                "quizup-game",
                "8.0.1",
                "pod-1",
                120,
                List.of(new SnapshotRequest.ProcessorRequest(
                        "game-projection",
                        "tracking",
                        true,
                        false,
                        true,
                        true,
                        true,
                        false,
                        true,
                        false,
                        "test-token-store",
                        1,
                        16,
                        List.of(),
                        List.of("1 segment(s) en erreur"),
                        List.of(new SnapshotRequest.SegmentRequest(
                                0, 0, false, true, false, false, 42L, null, 1, null, null)),
                        new SnapshotRequest.DlqRequest(2, 1))));
    }
}
