package io.github.quizup.console.infrastructure.out.client;

import io.github.quizup.console.infrastructure.config.ConsoleServicesProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ServiceActionClientTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
    private final ServiceActionClient client = new ServiceActionClient(
            restTemplate,
            new ConsoleServicesProperties(Map.of("quizup-game", "http://localhost:8082")));

    @Test
    void postsActionToInternalConsoleWithBody() {
        server.expect(requestTo("http://localhost:8082/internal/console/processors/game-projection/reset"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().json("{\"position\":\"TAIL\"}"))
                .andRespond(withSuccess("{\"reset\":true}", MediaType.APPLICATION_JSON));

        String body = client.post("quizup-game", "/processors/game-projection/reset",
                Map.of("position", "TAIL"));

        assertThat(body).contains("reset");
        server.verify();
    }

    @Test
    void mapsTargetErrorStatusToResponseStatusException() {
        server.expect(requestTo("http://localhost:8082/internal/console/dlq/game-projection/sequences/s1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("No dead-letter queue"));

        assertThatThrownBy(() -> client.delete("quizup-game", "/dlq/game-projection/sequences/s1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getsDlqSequencesPageFromTheTargetService() {
        server.expect(requestTo("http://localhost:8082/internal/console/dlq/game-projection?page=0&size=20"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("{\"sequences\":[]}", MediaType.APPLICATION_JSON));

        String body = client.get("quizup-game", "/dlq/game-projection?page=0&size=20");

        assertThat(body).contains("sequences");
        server.verify();
    }

    @Test
    void getsServiceRootEndpointWithoutInternalPrefix() {
        server.expect(requestTo("http://localhost:8082/query-capabilities"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("{\"queryNames\":[]}", MediaType.APPLICATION_JSON));

        String body = client.getService("quizup-game", "/query-capabilities");

        assertThat(body).contains("queryNames");
        server.verify();
    }

    @Test
    void defaultsToKubernetesServiceUrl() {
        ConsoleServicesProperties defaults = new ConsoleServicesProperties(Map.of());

        assertThat(defaults.baseUrl("quizup-game")).isEqualTo("http://quizup-game");
    }
}
