package io.github.quizup.console.domain.model;

import java.time.Instant;

/**
 * Entrée d'audit : qui a fait quoi, sur quelle cible, avec quel résultat.
 */
public record AuditEntry(
        Instant occurredAt,
        String actor,
        AuditAction action,
        String application,
        String instanceId,
        String target,
        String parameters,
        AuditResult result,
        String error
) {
}
