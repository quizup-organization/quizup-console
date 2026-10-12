package io.github.quizup.console.infrastructure.out.persistence.repository;

import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleSegmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsoleSegmentJpaRepository extends JpaRepository<ConsoleSegmentEntity, Long> {

    void deleteByApplicationAndInstanceId(String application, String instanceId);

    List<ConsoleSegmentEntity> findAllByApplicationAndInstanceIdAndProcessorOrderBySegmentIdAsc(
            String application, String instanceId, String processor);
}
