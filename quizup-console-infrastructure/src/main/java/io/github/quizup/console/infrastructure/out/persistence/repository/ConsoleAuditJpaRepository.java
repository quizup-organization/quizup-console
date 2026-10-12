package io.github.quizup.console.infrastructure.out.persistence.repository;

import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleAuditEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ConsoleAuditJpaRepository extends JpaRepository<ConsoleAuditEntity, Long> {

    Page<ConsoleAuditEntity> findAllByOrderByOccurredAtDesc(Pageable pageable);

    /** Acteurs distincts du journal d'audit (page « Utilisateurs »). */
    @Query("""
            select a.actor as actor, count(a) as total
            from ConsoleAuditEntity a
            where a.actor is not null
            group by a.actor
            order by count(a) desc
            """)
    List<ActorCount> findActorCounts();

    interface ActorCount {

        String getActor();

        long getTotal();
    }
}
