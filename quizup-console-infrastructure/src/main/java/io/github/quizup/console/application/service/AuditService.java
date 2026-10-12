package io.github.quizup.console.application.service;

import io.github.quizup.console.domain.model.AuditEntry;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleAuditEntity;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleAuditJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Audit des actions d'exploitation (qui / quoi / sur quelle cible / résultat).
 */
@Service
public class AuditService {

    private final ConsoleAuditJpaRepository repository;

    public AuditService(ConsoleAuditJpaRepository repository) {
        this.repository = repository;
    }

    public void record(AuditEntry entry) {
        repository.save(toEntity(entry));
    }

    public Page<AuditEntry> find(Pageable pageable) {
        return repository.findAllByOrderByOccurredAtDesc(pageable).map(AuditService::toEntry);
    }

    private static ConsoleAuditEntity toEntity(AuditEntry entry) {
        ConsoleAuditEntity entity = new ConsoleAuditEntity();
        entity.setOccurredAt(entry.occurredAt());
        entity.setActor(entry.actor());
        entity.setAction(entry.action());
        entity.setApplication(entry.application());
        entity.setInstanceId(entry.instanceId());
        entity.setTarget(entry.target());
        entity.setParameters(entry.parameters());
        entity.setResult(entry.result());
        entity.setError(entry.error());
        return entity;
    }

    private static AuditEntry toEntry(ConsoleAuditEntity entity) {
        return new AuditEntry(
                entity.getOccurredAt(),
                entity.getActor(),
                entity.getAction(),
                entity.getApplication(),
                entity.getInstanceId(),
                entity.getTarget(),
                entity.getParameters(),
                entity.getResult(),
                entity.getError());
    }
}
