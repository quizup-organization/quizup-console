package io.github.quizup.console.infrastructure.out.persistence.repository;

import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleProcessorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsoleProcessorJpaRepository extends JpaRepository<ConsoleProcessorEntity, Long> {

    void deleteByApplicationAndInstanceId(String application, String instanceId);

    List<ConsoleProcessorEntity> findAllByOrderByApplicationAscInstanceIdAscNameAsc();

    List<ConsoleProcessorEntity> findAllByApplicationOrderByInstanceIdAscNameAsc(String application);

    Optional<ConsoleProcessorEntity> findByApplicationAndInstanceIdAndName(String application,
                                                                           String instanceId,
                                                                           String name);
}
