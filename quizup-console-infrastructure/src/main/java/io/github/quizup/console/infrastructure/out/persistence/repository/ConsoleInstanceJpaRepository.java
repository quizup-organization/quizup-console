package io.github.quizup.console.infrastructure.out.persistence.repository;

import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsoleInstanceJpaRepository extends JpaRepository<ConsoleInstanceEntity, Long> {

    Optional<ConsoleInstanceEntity> findByApplicationAndInstanceId(String application, String instanceId);

    List<ConsoleInstanceEntity> findAllByOrderByApplicationAscInstanceIdAsc();
}
