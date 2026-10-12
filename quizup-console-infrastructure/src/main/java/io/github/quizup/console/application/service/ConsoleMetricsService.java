package io.github.quizup.console.application.service;

import io.github.quizup.console.infrastructure.out.prometheus.PrometheusClient;
import io.github.quizup.console.infrastructure.out.prometheus.PrometheusClient.PrometheusResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Requêtes Prometheus fixes des écrans d'exploitation (aucune PromQL arbitraire exposée par l'API).
 * Fenêtres : {@code 1h} (défaut), {@code 6h}, {@code 24h}.
 */
@Service
public class ConsoleMetricsService {

    private final PrometheusClient prometheus;

    public ConsoleMetricsService(PrometheusClient prometheus) {
        this.prometheus = prometheus;
    }

    public boolean enabled() {
        return prometheus.enabled();
    }

    public MessageRates messageRates() {
        return new MessageRates(
                prometheus.query("sum by (application) (rate(quizup_axon_commands_total[5m]))"),
                prometheus.query("sum by (application) (rate(quizup_axon_queries_total[5m]))"),
                prometheus.query("sum by (application) (rate(quizup_axon_events_processed_total{result=\"success\"}[5m]))"),
                prometheus.query("sum by (application) (rate(quizup_axon_events_processed_total{result=\"failure\"}[5m]))"));
    }

    public ProcessorMetrics processorMetrics(String application, String processor, String range) {
        Window window = Window.of(range);
        String selector = "{application=\"" + escape(application) + "\",processor=\"" + escape(processor) + "\"}";
        return new ProcessorMetrics(
                prometheus.queryRange(
                        "sum by (result) (rate(quizup_axon_events_processed_total" + selector + "[5m]))",
                        window.start(), window.end(), window.step()),
                prometheus.queryRange(
                        "histogram_quantile(0.9, sum by (le) (rate(quizup_axon_event_duration_seconds_bucket"
                                + selector + "[5m])))",
                        window.start(), window.end(), window.step()),
                prometheus.queryRange("quizup_axon_console_dlq_size" + selector,
                        window.start(), window.end(), window.step()),
                prometheus.queryRange("quizup_axon_console_processor_caught_up" + selector,
                        window.start(), window.end(), window.step()),
                prometheus.queryRange("quizup_axon_console_processor_replaying" + selector,
                        window.start(), window.end(), window.step()));
    }

    public ApplicationMetrics applicationMetrics(String application, String range) {
        Window window = Window.of(range);
        String selector = "{application=\"" + escape(application) + "\"}";
        return new ApplicationMetrics(
                prometheus.queryRange("jvm_memory_used_bytes{application=\"" + escape(application)
                        + "\",area=\"heap\"}", window.start(), window.end(), window.step()),
                prometheus.queryRange("process_cpu_usage" + selector,
                        window.start(), window.end(), window.step()),
                prometheus.queryRange("system_cpu_usage" + selector,
                        window.start(), window.end(), window.step()),
                prometheus.queryRange("jvm_threads_live_threads" + selector,
                        window.start(), window.end(), window.step()),
                prometheus.queryRange("sum(rate(http_server_requests_seconds_count" + selector + "[5m]))",
                        window.start(), window.end(), window.step()));
    }

    public MessageMetrics messageMetrics(String application, String range) {
        Window window = Window.of(range);
        String selector = "{application=\"" + escape(application) + "\"}";
        return new MessageMetrics(
                prometheus.queryRange("sum by (name, result) (rate(quizup_axon_commands_total"
                        + selector + "[5m]))", window.start(), window.end(), window.step()),
                prometheus.queryRange("sum by (name, result) (rate(quizup_axon_queries_total"
                        + selector + "[5m]))", window.start(), window.end(), window.step()),
                prometheus.queryRange("sum by (name, result) (rate(quizup_axon_events_processed_total"
                        + selector + "[5m]))", window.start(), window.end(), window.step()));
    }

    private static String escape(String labelValue) {
        return labelValue == null ? "" : labelValue.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Window(Instant start, Instant end, Duration step) {

        static Window of(String range) {
            Duration duration = switch (range == null ? "1h" : range) {
                case "6h" -> Duration.ofHours(6);
                case "24h" -> Duration.ofHours(24);
                default -> Duration.ofHours(1);
            };
            Duration step = duration.dividedBy(120);
            if (step.compareTo(Duration.ofSeconds(15)) < 0) {
                step = Duration.ofSeconds(15);
            }
            Instant end = Instant.now();
            return new Window(end.minus(duration), end, step);
        }
    }

    // ------------------------------------------------------------------ contracts

    public record MessageRates(PrometheusResult commands, PrometheusResult queries,
                               PrometheusResult eventsProcessed, PrometheusResult eventsFailed) {
    }

    public record ProcessorMetrics(PrometheusResult processed, PrometheusResult durationP90,
                                   PrometheusResult dlqSize, PrometheusResult caughtUp,
                                   PrometheusResult replaying) {
    }

    public record ApplicationMetrics(PrometheusResult heapUsed, PrometheusResult processCpu,
                                     PrometheusResult systemCpu, PrometheusResult threads,
                                     PrometheusResult httpRate) {
    }

    public record MessageMetrics(PrometheusResult commands, PrometheusResult queries,
                                 PrometheusResult events) {
    }
}
