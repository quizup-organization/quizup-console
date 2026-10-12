package io.github.quizup.console.domain.model;

import java.time.Instant;

/**
 * Instance d'une application connectée à la console (une par réplica), vue de la dernière
 * ingestion d'agent.
 */
public record InstanceState(
        String application,
        String instanceId,
        String version,
        long uptimeSeconds,
        Instant firstSeen,
        Instant lastSeen
) {
}
