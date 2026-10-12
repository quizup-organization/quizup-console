package io.github.quizup.console.infrastructure.out.persistence.repository;

import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleDlqStatEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsoleDlqStatJpaRepository extends JpaRepository<ConsoleDlqStatEntity, Long> {

    void deleteByApplicationAndInstanceId(String application, String instanceId);

    List<ConsoleDlqStatEntity> findAllByOrderByApplicationAscInstanceIdAscProcessorAsc();
}
