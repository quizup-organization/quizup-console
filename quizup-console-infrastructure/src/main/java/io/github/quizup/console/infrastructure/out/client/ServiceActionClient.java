package io.github.quizup.console.infrastructure.out.client;

import io.github.quizup.console.infrastructure.config.ConsoleServicesProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Client M2M vers l'API interne d'exploitation des services ({@code /internal/console/**}).
 * Réutilise le {@link RestTemplate} OAuth2 {@code @Primary} du SDK (client credentials
 * {@code server-client}).
 */
@Component
public class ServiceActionClient {

    private static final String INTERNAL_CONSOLE_PATH = "/internal/console";

    private final RestTemplate restTemplate;
    private final ConsoleServicesProperties properties;

    public ServiceActionClient(RestTemplate restTemplate, ConsoleServicesProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public String get(String application, String path) {
        return exchange(application, HttpMethod.GET, path, null);
    }

    public String post(String application, String path) {
        return exchange(application, HttpMethod.POST, path, null);
    }

    public String delete(String application, String path) {
        return exchange(application, HttpMethod.DELETE, path, null);
    }

    public String post(String application, String path, Object body) {
        return exchange(application, HttpMethod.POST, path, body);
    }

    /**
     * Appel GET d'un endpoint <b>racine</b> du service (ex. {@code /query-capabilities},
     * {@code /command-capabilities}) — hors préfixe interne.
     */
    public String getService(String application, String path) {
        return exchangeUrl(properties.baseUrl(application) + path, HttpMethod.GET, null);
    }

    private String exchange(String application, HttpMethod method, String path, Object body) {
        return exchangeUrl(properties.baseUrl(application) + INTERNAL_CONSOLE_PATH + path, method, body);
    }

    private String exchangeUrl(String url, HttpMethod method, Object body) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, method, new HttpEntity<>(body), String.class);
            return response.getBody();
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(exception.getStatusCode(), exception.getResponseBodyAsString());
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Service unreachable: " + url, exception);
        }
    }
}
