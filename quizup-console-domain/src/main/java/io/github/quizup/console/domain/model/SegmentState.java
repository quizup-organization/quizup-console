package io.github.quizup.console.domain.model;

/**
 * État d'un segment d'event processor (position, caught-up, replay, erreur).
 */
public record SegmentState(
        String application,
        String instanceId,
        String processor,
        int segmentId,
        int mask,
        boolean caughtUp,
        boolean replaying,
        boolean merging,
        boolean errorState,
        Long currentPosition,
        Long resetPosition,
        String errorType,
        String errorMessage
) {
}
