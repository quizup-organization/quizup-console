package io.github.quizup.console.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * URLs des services ciblés par les actions d'exploitation (`quizup.console.services.urls`).
 *
 * <p>En production, la valeur par défaut suffit : les Services Kubernetes portent le nom de
 * l'application et exposent le port 80 (`http://<application>`). En local, on surcharge chaque
 * service avec son port (`application-local.yml`).</p>
 */
@ConfigurationProperties(prefix = "quizup.console.services")
public record ConsoleServicesProperties(
        Map<String, String> urls
) {

    private static final String DEFAULT_TEMPLATE = "http://%s";

    public ConsoleServicesProperties {
        if (urls == null) {
            urls = Map.of();
        }
    }

    public String baseUrl(String application) {
        return urls.getOrDefault(application, DEFAULT_TEMPLATE.formatted(application));
    }
}
