package io.github.quizup.console.infrastructure.in.api.request;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot d'exploitation poussé par l'agent SDK ({@code ConsoleSnapshot} v1,
 * {@code POST /internal/ingest/snapshot}). Contrat <b>propre à la console</b>, volontairement
 * tolérant (Boot ignore les champs inconnus) : la console doit accepter les payloads des différentes
 * versions d'agent.
 */
public record SnapshotRequest(
        String version,
        Instant timestamp,
        String application,
        String applicationVersion,
        String instanceId,
        long uptimeSeconds,
        List<ProcessorRequest> processors
) {

    public record ProcessorRequest(
            String name,
            String mode,
            boolean running,
            boolean error,
            boolean replaying,
            boolean supportsReset,
            boolean canPause,
            boolean canPlay,
            boolean canSplit,
            boolean canMerge,
            String tokenStoreIdentifier,
            int activeThreads,
            int capacity,
            List<String> freeThreadInstances,
            List<String> warnings,
            List<SegmentRequest> segments,
            DlqRequest dlq
    ) {
    }

    public record SegmentRequest(
            int segmentId,
            int mask,
            boolean caughtUp,
            boolean replaying,
            boolean merging,
            boolean errorState,
            Long currentPosition,
            Long resetPosition,
            int onePartOf,
            String errorType,
            String errorMessage
    ) {
    }

    public record DlqRequest(long size, long sequences) {
    }
}
