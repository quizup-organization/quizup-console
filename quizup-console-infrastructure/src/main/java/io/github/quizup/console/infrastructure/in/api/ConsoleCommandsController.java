package io.github.quizup.console.infrastructure.in.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quizup.console.infrastructure.out.client.ServiceActionClient;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleInstanceEntity;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleInstanceJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Commandes <b>en vol</b> (contenu natif « long-running commands »), agrégées par type sur les
 * instances d'une application (ou toutes). Lecture seule : Axon Framework ne permet pas d'annuler
 * une commande en cours.
 */
@RestController
@RequestMapping("/api/console")
@PreAuthorize("hasRole('ADMIN')")
public class ConsoleCommandsController {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleCommandsController.class);

    private final ConsoleInstanceJpaRepository instanceRepository;
    private final ServiceActionClient client;
    private final ObjectMapper objectMapper;

    public ConsoleCommandsController(ConsoleInstanceJpaRepository instanceRepository,
                                     ServiceActionClient client,
                                     ObjectMapper objectMapper) {
        this.instanceRepository = instanceRepository;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/commands/in-flight")
    public List<InFlightCommandView> inFlight(@RequestParam(required = false) String application) {
        List<ConsoleInstanceEntity> instances = instanceRepository.findAllByOrderByApplicationAscInstanceIdAsc()
                .stream()
                .filter(instance -> application == null || application.isBlank()
                        || instance.getApplication().equals(application))
                .toList();

        Map<String, Acc> aggregated = new LinkedHashMap<>();
        for (ConsoleInstanceEntity instance : instances) {
            try {
                String body = instance.getAddress() != null && !instance.getAddress().isBlank()
                        ? client.getAt(instance.getAddress(), "/commands/in-flight")
                        : client.get(instance.getApplication(), "/commands/in-flight");
                JsonNode entries = objectMapper.readTree(body == null ? "[]" : body);
                for (JsonNode entry : entries) {
                    String name = entry.path("name").asText("unknown");
                    Acc acc = aggregated.computeIfAbsent(name, key -> new Acc());
                    acc.count += entry.path("count").asInt(0);
                    acc.oldest = min(acc.oldest, parse(entry.path("oldestStart").asText(null)));
                    acc.last = max(acc.last, parse(entry.path("lastStart").asText(null)));
                    acc.instances.add(instance.getInstanceId());
                }
            } catch (Exception exception) {
                logger.warn("Could not read in-flight commands of {} ({}): {}",
                        instance.getApplication(), instance.getInstanceId(), exception.toString());
            }
        }

        return aggregated.entrySet()
                .stream()
                .map(entry -> new InFlightCommandView(entry.getKey(), entry.getValue().count,
                        entry.getValue().oldest, entry.getValue().last,
                        List.copyOf(entry.getValue().instances)))
                .sorted(Comparator.comparing(InFlightCommandView::oldestStart,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static Instant parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Instant min(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private static Instant max(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private static final class Acc {

        private int count;
        private Instant oldest;
        private Instant last;
        private final List<String> instances = new ArrayList<>();
    }

    public record InFlightCommandView(String name, int count, Instant oldestStart, Instant lastStart,
                                      List<String> instances) {
    }
}
