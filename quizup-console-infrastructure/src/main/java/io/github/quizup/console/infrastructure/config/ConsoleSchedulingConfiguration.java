package io.github.quizup.console.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active les tâches planifiées (purge des instances fantômes). */
@Configuration
@EnableScheduling
public class ConsoleSchedulingConfiguration {
}
