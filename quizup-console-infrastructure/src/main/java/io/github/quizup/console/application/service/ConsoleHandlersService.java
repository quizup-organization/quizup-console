package io.github.quizup.console.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quizup.console.infrastructure.out.client.ServiceActionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Liste des handlers <b>commandes</b> et <b>queries</b> d'une application, lue depuis les
 * endpoints de capacités déjà exposés par les services
 * ({@code /command-capabilities} — filtre Axon sérialisé — et {@code /query-capabilities}).
 * Résultat mis en cache 30 s (les handlers ne changent qu'au déploiement).
 */
@Service
public class ConsoleHandlersService {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleHandlersService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final ServiceActionClient client;
    private final ObjectMapper objectMapper;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public ConsoleHandlersService(ServiceActionClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public Handlers handlers(String application, List<String> instanceIds) {
        Cached cached = cache.get(application);
        if (cached != null && cached.at().plus(CACHE_TTL).isAfter(Instant.now())) {
            return cached.handlers();
        }
        Handlers handlers = fetch(application, instanceIds);
        cache.put(application, new Cached(Instant.now(), handlers));
        return handlers;
    }

    private Handlers fetch(String application, List<String> instanceIds) {
        for (String instanceId : instanceIds) {
            try {
                Set<String> queries = parseQueries(client.getService(application, "/query-capabilities"));
                Set<String> commands = parseCommands(client.getService(application, "/command-capabilities"));
                return new Handlers(List.copyOf(commands), List.copyOf(queries));
            } catch (Exception exception) {
                logger.debug("Could not read capabilities of {} ({}): {}",
                        application, instanceId, exception.toString());
            }
        }
        logger.warn("Could not read capabilities of application {}", application);
        return new Handlers(List.of(), List.of());
    }

    private Set<String> parseQueries(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return readNames(root.path("queryNames"));
        } catch (Exception exception) {
            throw new IllegalStateException("Unreadable query capabilities", exception);
        }
    }

    private Set<String> parseCommands(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String serializedFilter = root.path("serializedCommandFilter").asText("");
            if (serializedFilter.isBlank()) {
                return Set.of();
            }
            JsonNode filter = objectMapper.readTree(serializedFilter);
            return readNames(filter.path("commandNames"));
        } catch (Exception exception) {
            throw new IllegalStateException("Unreadable command capabilities", exception);
        }
    }

    private static Set<String> readNames(JsonNode array) {
        Set<String> names = new TreeSet<>();
        if (array.isArray()) {
            array.forEach(node -> {
                if (!node.asText().isBlank()) {
                    names.add(node.asText());
                }
            });
        }
        return names;
    }

    private record Cached(Instant at, Handlers handlers) {
    }

    public record Handlers(List<String> commands, List<String> queries) {

        public List<String> commands() {
            return commands == null ? List.of() : new ArrayList<>(commands);
        }

        public List<String> queries() {
            return queries == null ? List.of() : new ArrayList<>(queries);
        }
    }
}
