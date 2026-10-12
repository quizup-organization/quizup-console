package io.github.quizup.console.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * La console est une surface d'<b>exploitation</b> : l'API de lecture et les actions sont réservées
 * aux administrateurs (`ROLE_ADMIN`, claim `roles` émis par {@code quizup-identity}).
 */
@Configuration
@EnableMethodSecurity
public class ConsoleSecurityConfiguration {
}
