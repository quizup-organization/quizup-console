package io.github.quizup.console.infrastructure.out.persistence.repository;

import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleAuditEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsoleAuditJpaRepository extends JpaRepository<ConsoleAuditEntity, Long> {

    Page<ConsoleAuditEntity> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
