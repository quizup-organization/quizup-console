package io.github.quizup.console.domain.model;

/**
 * État d'un event processor sur une instance (dernière ingestion d'agent).
 */
public record ProcessorState(
        String application,
        String instanceId,
        String name,
        String mode,
        boolean running,
        boolean error,
        boolean replaying,
        boolean supportsReset
) {
}
