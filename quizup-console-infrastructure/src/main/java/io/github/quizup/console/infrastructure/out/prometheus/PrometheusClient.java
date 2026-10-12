package io.github.quizup.console.infrastructure.out.prometheus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quizup.console.infrastructure.config.QuizupConsolePrometheusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lecture de l'API HTTP Prometheus (`/api/v1/query`, `/api/v1/query_range`) pour les écrans
 * d'exploitation. Toute erreur réseau/API renvoie un résultat vide <b>enabled</b> (jamais
 * d'exception : les pages restent consultables sans observabilité).
 */
public class PrometheusClient {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusClient.class);

    private final RestTemplate restTemplate;
    private final QuizupConsolePrometheusProperties properties;
    private final ObjectMapper objectMapper;

    public PrometheusClient(RestTemplate restTemplate,
                            QuizupConsolePrometheusProperties properties,
                            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public PrometheusResult query(String promql) {
        if (!enabled()) {
            return PrometheusResult.disabled();
        }
        String uri = UriComponentsBuilder.fromUriString(properties.url())
                .path("/api/v1/query")
                .queryParam("query", promql)
                .build()
                .encode()
                .toUriString();
        return execute(uri, false);
    }

    public PrometheusResult queryRange(String promql, Instant start, Instant end, Duration step) {
        if (!enabled()) {
            return PrometheusResult.disabled();
        }
        String uri = UriComponentsBuilder.fromUriString(properties.url())
                .path("/api/v1/query_range")
                .queryParam("query", promql)
                .queryParam("start", start.getEpochSecond())
                .queryParam("end", end.getEpochSecond())
                .queryParam("step", step.toSeconds())
                .build()
                .encode()
                .toUriString();
        return execute(uri, true);
    }

    private PrometheusResult execute(String uri, boolean range) {
        try {
            String body = restTemplate.getForObject(uri, String.class);
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            List<PrometheusSeries> series = new ArrayList<>();
            for (JsonNode result : root.path("data").path("result")) {
                Map<String, String> metric = new LinkedHashMap<>();
                result.path("metric").fields().forEachRemaining(entry -> metric.put(entry.getKey(), entry.getValue().asText()));
                List<PrometheusSample> samples = new ArrayList<>();
                if (range) {
                    for (JsonNode value : result.path("values")) {
                        samples.add(sample(value));
                    }
                } else {
                    JsonNode value = result.path("value");
                    if (!value.isMissingNode()) {
                        samples.add(sample(value));
                    }
                }
                series.add(new PrometheusSeries(metric, samples));
            }
            return new PrometheusResult(true, series);
        } catch (RuntimeException | java.io.IOException exception) {
            logger.warn("Prometheus query failed ({}): {}", uri, exception.toString());
            return PrometheusResult.error();
        }
    }

    private static PrometheusSample sample(JsonNode value) {
        Instant timestamp = Instant.ofEpochMilli(
                value.path(0).decimalValue().multiply(java.math.BigDecimal.valueOf(1000)).longValue());
        double sample = value.path(1).asDouble(Double.NaN);
        return new PrometheusSample(timestamp, sample);
    }

    // ------------------------------------------------------------------ contracts

    public record PrometheusResult(boolean enabled, List<PrometheusSeries> series) {

        static PrometheusResult disabled() {
            return new PrometheusResult(false, List.of());
        }

        static PrometheusResult error() {
            return new PrometheusResult(true, List.of());
        }

        public double total() {
            return series.stream()
                    .flatMap(entry -> entry.samples().stream())
                    .mapToDouble(PrometheusSample::value)
                    .filter(Double::isFinite)
                    .sum();
        }
    }

    public record PrometheusSeries(Map<String, String> metric, List<PrometheusSample> samples) {
    }

    public record PrometheusSample(Instant timestamp, double value) {
    }
}
