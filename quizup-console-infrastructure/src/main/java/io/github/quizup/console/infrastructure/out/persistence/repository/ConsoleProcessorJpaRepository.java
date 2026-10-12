package io.github.quizup.console.infrastructure.out.persistence.repository;

import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleProcessorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConsoleProcessorJpaRepository extends JpaRepository<ConsoleProcessorEntity, Long> {

    /**
     * Suppression <b>bulk</b> (SQL immédiat) : indispensable avant la ré-insertion des lignes d'une
     * instance — avec les deletes dérivés, Hibernate exécute les inserts avant les deletes dans la
     * même transaction et viole la contrainte unique `uk_console_processor`.
     */
    @Modifying
    @Query("delete from ConsoleProcessorEntity e where e.application = :application and e.instanceId = :instanceId")
    void deleteByApplicationAndInstanceId(@Param("application") String application,
                                          @Param("instanceId") String instanceId);

    List<ConsoleProcessorEntity> findAllByOrderByApplicationAscInstanceIdAscNameAsc();

    List<ConsoleProcessorEntity> findAllByApplicationOrderByInstanceIdAscNameAsc(String application);

    Optional<ConsoleProcessorEntity> findByApplicationAndInstanceIdAndName(String application,
                                                                           String instanceId,
                                                                           String name);
}
