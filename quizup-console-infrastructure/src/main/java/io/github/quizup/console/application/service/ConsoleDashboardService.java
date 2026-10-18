package io.github.quizup.console.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quizup.console.infrastructure.out.client.ServiceActionClient;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleDlqStatEntity;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleProcessorEntity;
import io.github.quizup.console.infrastructure.out.persistence.entity.ConsoleSegmentEntity;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleDlqStatJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleInstanceJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleProcessorJpaRepository;
import io.github.quizup.console.infrastructure.out.persistence.repository.ConsoleSegmentJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Agrégats du <b>tableau de bord</b> : état des réplicas/processors/DLQ (DB console), débits de
 * messages (Prometheus) et volumétrie des event stores JPA (API interne des services, cache 30 s).
 */
@Service
public class ConsoleDashboardService {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleDashboardService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final ConsoleInstanceJpaRepository instanceRepository;
    private final ConsoleProcessorJpaRepository processorRepository;
    private final ConsoleDlqStatJpaRepository dlqStatRepository;
    private final ConsoleSegmentJpaRepository segmentRepository;
    private final ConsoleMetricsService metricsService;
    private final ServiceActionClient client;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<BuildProperties> buildProperties;

    private volatile CachedEventStore cachedEventStore;

    public ConsoleDashboardService(ConsoleInstanceJpaRepository instanceRepository,
                                   ConsoleProcessorJpaRepository processorRepository,
                                   ConsoleDlqStatJpaRepository dlqStatRepository,
                                   ConsoleSegmentJpaRepository segmentRepository,
                                   ConsoleMetricsService metricsService,
                                   ServiceActionClient client,
                                   ObjectMapper objectMapper,
                                   ObjectProvider<BuildProperties> buildProperties) {
        this.instanceRepository = instanceRepository;
        this.processorRepository = processorRepository;
        this.dlqStatRepository = dlqStatRepository;
        this.segmentRepository = segmentRepository;
        this.metricsService = metricsService;
        this.client = client;
        this.objectMapper = objectMapper;
        this.buildProperties = buildProperties;
    }

    /** Version du service (build-info), sans agrégation dashboard. */
    public Product version() {
        BuildProperties build = buildProperties.getIfAvailable();
        return new Product("QuizUp Console",
                build != null && build.getVersion() != null ? build.getVersion() : "unknown");
    }

    public DashboardResponse dashboard() {
        List<ConsoleProcessorEntity> processors = processorRepository.findAll();
        List<ConsoleDlqStatEntity> dlqStats = dlqStatRepository.findAll();

        long inError = processors.stream().filter(ConsoleProcessorEntity::isError).count();
        long replaying = processors.stream().filter(ConsoleProcessorEntity::isReplaying).count();
        long notCaughtUp = segmentRepository.findAll()
                .stream()
                .filter(segment -> !segment.isCaughtUp())
                .map(segment -> segment.getApplication() + "/" + segment.getInstanceId() + "/" + segment.getProcessor())
                .distinct()
                .count();
        long dlqMessages = dlqStats.stream().mapToLong(ConsoleDlqStatEntity::getSize).sum();
        long dlqSequences = dlqStats.stream().mapToLong(ConsoleDlqStatEntity::getSequences).sum();

        Counts counts = new Counts(
                instanceRepository.count(),
                processors.size(),
                inError,
                replaying,
                notCaughtUp,
                dlqStats.size(),
                dlqMessages,
                dlqSequences);

        List<String> applications = processors.stream()
                .map(ConsoleProcessorEntity::getApplication)
                .distinct()
                .sorted()
                .toList();

        Product product = version();

        return new DashboardResponse(product, counts, metricsService.messageRates(),
                eventStoreTotals(applications), metricsService.storage());
    }

    private EventStoreTotals eventStoreTotals(List<String> applications) {
        CachedEventStore cached = cachedEventStore;
        if (cached != null && cached.at().plus(CACHE_TTL).isAfter(Instant.now())
                && cached.applications().equals(applications)) {
            return cached.totals();
        }
        List<ApplicationEventStore> perApplication = new ArrayList<>();
        long totalEvents = 0;
        long totalSnapshots = 0;
        for (String application : applications) {
            try {
                JsonNode stats = objectMapper.readTree(client.get(application, "/eventstore/stats"));
                long events = stats.path("events").asLong(0);
                long snapshots = stats.path("snapshots").asLong(0);
                totalEvents += events;
                totalSnapshots += snapshots;
                perApplication.add(new ApplicationEventStore(application, events, snapshots, true));
            } catch (Exception exception) {
                logger.warn("Could not read event store stats of {}: {}", application, exception.toString());
                perApplication.add(new ApplicationEventStore(application, 0, 0, false));
            }
        }
        EventStoreTotals totals = new EventStoreTotals(totalEvents, totalSnapshots, perApplication);
        cachedEventStore = new CachedEventStore(Instant.now(),
                new TreeSet<>(applications).stream().collect(Collectors.toList()), totals);
        return totals;
    }

    private record CachedEventStore(Instant at, List<String> applications, EventStoreTotals totals) {
    }

    // ------------------------------------------------------------------ contracts

    public record DashboardResponse(Product product, Counts counts, ConsoleMetricsService.MessageRates messageRates,
                                    EventStoreTotals eventStore, ConsoleMetricsService.StorageMetrics storage) {
    }

    public record Product(String name, String version) {
    }

    public record Counts(long instances, long processors, long processorsInError, long processorsReplaying,
                         long processorsNotCaughtUp, long dlqProcessors, long dlqMessages, long dlqSequences) {
    }

    public record EventStoreTotals(long events, long snapshots, List<ApplicationEventStore> applications) {
    }

    public record ApplicationEventStore(String application, long events, long snapshots, boolean reachable) {
    }
}
