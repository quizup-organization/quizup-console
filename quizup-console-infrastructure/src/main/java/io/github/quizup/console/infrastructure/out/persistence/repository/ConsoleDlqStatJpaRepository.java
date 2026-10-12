package io.github.quizup.console.infrastructure.out.persistence.repository;

import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleDlqStatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConsoleDlqStatJpaRepository extends JpaRepository<ConsoleDlqStatEntity, Long> {

    /** Suppression bulk (SQL immédiat) — cf. {@link ConsoleProcessorJpaRepository}. */
    @Modifying
    @Query("delete from ConsoleDlqStatEntity e where e.application = :application and e.instanceId = :instanceId")
    void deleteByApplicationAndInstanceId(@Param("application") String application,
                                          @Param("instanceId") String instanceId);

    List<ConsoleDlqStatEntity> findAllByOrderByApplicationAscInstanceIdAscProcessorAsc();
}
