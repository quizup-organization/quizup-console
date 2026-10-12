package io.github.quizup.console.infrastructure.out.persistence.repository;

import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleSegmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConsoleSegmentJpaRepository extends JpaRepository<ConsoleSegmentEntity, Long> {

    /** Suppression bulk (SQL immédiat) — cf. {@link ConsoleProcessorJpaRepository}. */
    @Modifying
    @Query("delete from ConsoleSegmentEntity e where e.application = :application and e.instanceId = :instanceId")
    void deleteByApplicationAndInstanceId(@Param("application") String application,
                                          @Param("instanceId") String instanceId);

    List<ConsoleSegmentEntity> findAllByApplicationAndInstanceIdAndProcessorOrderBySegmentIdAsc(
            String application, String instanceId, String processor);
}
