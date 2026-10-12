package io.github.quizup.console.infrastructure.out.prometheus;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quizup.console.infrastructure.config.QuizupConsolePrometheusProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PrometheusClientTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PrometheusClient client(String url) {
        return new PrometheusClient(restTemplate,
                new QuizupConsolePrometheusProperties(url), objectMapper);
    }

    @Test
    void parsesInstantQueryResults() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/v1/query?")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"result":[
                          {"metric":{"application":"quizup-game"},"value":[1791792493.159,"1.5"]}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        var result = client("http://prometheus:9090").query("sum(rate(x[5m]))");

        assertThat(result.enabled()).isTrue();
        assertThat(result.total()).isEqualTo(1.5);
        assertThat(result.series().getFirst().metric()).containsEntry("application", "quizup-game");
        assertThat(result.series().getFirst().samples().getFirst().timestamp())
                .isEqualTo(Instant.ofEpochMilli(1791792493159L));
    }

    @Test
    void parsesRangeQueryResults() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/v1/query_range?")))
                .andRespond(withSuccess("""
                        {"status":"success","data":{"result":[
                          {"metric":{},"values":[[1791792493.6,"2.0"],[1791792523.6,"3.0"]]}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        var result = client("http://prometheus:9090")
                .queryRange("up", Instant.now().minusSeconds(600), Instant.now(), Duration.ofSeconds(30));

        assertThat(result.series()).hasSize(1);
        assertThat(result.series().getFirst().samples()).hasSize(2);
        assertThat(result.total()).isEqualTo(5.0);
    }

    @Test
    void disabledWhenNoUrlConfigured() {
        var result = client("").query("up");

        assertThat(result.enabled()).isFalse();
        assertThat(result.series()).isEmpty();
    }

    @Test
    void returnsEmptyOnServerError() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/api/v1/query?")))
                .andRespond(withServerError());

        var result = client("http://prometheus:9090").query("up");

        assertThat(result.enabled()).isTrue();
        assertThat(result.series()).isEmpty();
    }
}
