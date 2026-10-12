package io.github.quizup.console.domain.model;

/**
 * Taille de la dead-letter queue d'un processing group (dernière ingestion d'agent).
 */
public record DlqState(
        String application,
        String instanceId,
        String processor,
        long size,
        long sequences
) {
}
