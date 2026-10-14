package com.startupsimulator.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupsimulator.config.AiExecutionMode;
import com.startupsimulator.config.LlmProperties;
import com.startupsimulator.model.Startup;
import com.startupsimulator.resilience.RetryExecutor;
import com.startupsimulator.resilience.Sleeper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JustDoWorkLLMServiceTest {

    private ObjectMapper objectMapper;
    private LlmProperties props;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        props = new LlmProperties();
        props.setProvider("justdowork");
        props.setMode(AiExecutionMode.REAL);
        props.getJustdowork().setApiKey("test-justdowork-key-123");
        props.getJustdowork().setBaseUrl("https://api.justwoker.icu/v1");
        props.getJustdowork().setModel("claude-opus-4-8");
    }

    @Test
    void providerSelection_returnsJustDoWork() {
        assertThat(props.getProvider()).isEqualTo("justdowork");
        JustDoWorkLLMService service = new JustDoWorkLLMService(props, objectMapper);
        assertThat(service.provider()).isEqualTo("justdowork");
        assertThat(service.isRealProvider()).isTrue();
    }

    @Test
    void configuration_hasCorrectBaseUrlAndModelDefaults() {
        LlmProperties defaultProps = new LlmProperties();
        assertThat(defaultProps.getJustdowork().getBaseUrl()).isEqualTo("https://api.justwoker.icu/v1");
        assertThat(defaultProps.getJustdowork().getModel()).isEqualTo("claude-opus-4-8");
    }

    @Test
    void generate_sendsCorrectAuthorizationHeaderAndModel() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.getJustdowork().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getJustdowork().getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        mockServer.expect(requestTo("https://api.justwoker.icu/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-justdowork-key-123"))
                .andExpect(jsonPath("$.model").value("claude-opus-4-8"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hello world\"}}]}", MediaType.APPLICATION_JSON));

        JustDoWorkLLMService service = new JustDoWorkLLMService(props, objectMapper, restClient, new RetryExecutor(Sleeper.NO_OP));
        String response = service.generate("System prompt", "User prompt");

        assertThat(response).isEqualTo("Hello world");
        mockServer.verify();
    }

    @Test
    void apiKey_isNeverLoggedOrExposedInExceptions() {
        props.getJustdowork().setApiKey("super-secret-api-key-999");
        JustDoWorkLLMService service = new JustDoWorkLLMService(props, objectMapper);
        assertThat(service.isRealProvider()).isTrue();

        // Cause a failure by pointing to an unresolvable port/endpoint
        props.getJustdowork().setBaseUrl("http://localhost:65534");
        JustDoWorkLLMService failingService = new JustDoWorkLLMService(props, objectMapper);

        assertThatThrownBy(() -> failingService.generate("system", "user"))
                .isInstanceOf(CeoAnalysisException.class)
                .hasMessageNotContaining("super-secret-api-key-999");
    }

    @Test
    void successfulResponseParsing_structuredOutput() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.getJustdowork().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getJustdowork().getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        String jsonResponse = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"name\\\":\\\"Flexi AI\\\",\\\"status\\\":\\\"ACTIVE\\\"}\"}}]}";
        mockServer.expect(requestTo("https://api.justwoker.icu/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        JustDoWorkLLMService service = new JustDoWorkLLMService(props, objectMapper, restClient, new RetryExecutor(Sleeper.NO_OP));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = service.generateStructured("System prompt", "User prompt", Map.class);

        assertThat(result.get("name")).isEqualTo("Flexi AI");
        assertThat(result.get("status")).isEqualTo("ACTIVE");
        mockServer.verify();
    }

    @Test
    void malformedResponseHandling_throwsCeoAnalysisException() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.getJustdowork().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getJustdowork().getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        mockServer.expect(requestTo("https://api.justwoker.icu/v1/chat/completions"))
                .andRespond(withSuccess("{\"unexpected\": true}", MediaType.APPLICATION_JSON));

        JustDoWorkLLMService service = new JustDoWorkLLMService(props, objectMapper, restClient, new RetryExecutor(Sleeper.NO_OP));

        assertThatThrownBy(() -> service.generate("System prompt", "User prompt"))
                .isInstanceOf(CeoAnalysisException.class)
                .hasMessageContaining("JustDoWork returned an empty completion");
    }

    @Test
    void transientFailureRetry_succeedsOnSecondAttempt() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.getJustdowork().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getJustdowork().getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        // 1st request fails with 500
        mockServer.expect(requestTo("https://api.justwoker.icu/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // 2nd request succeeds with 200
        mockServer.expect(requestTo("https://api.justwoker.icu/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Recovered response\"}}]}", MediaType.APPLICATION_JSON));

        RetryExecutor retryExecutor = new RetryExecutor(Sleeper.NO_OP);
        JustDoWorkLLMService service = new JustDoWorkLLMService(props, objectMapper, restClient, retryExecutor);

        String result = service.generate("System prompt", "User prompt");
        assertThat(result).isEqualTo("Recovered response");
        mockServer.verify();
    }

    @Test
    void retryExhaustion_throwsExplicitException() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.getJustdowork().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getJustdowork().getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        mockServer.expect(requestTo("https://api.justwoker.icu/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        mockServer.expect(requestTo("https://api.justwoker.icu/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        mockServer.expect(requestTo("https://api.justwoker.icu/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        RetryExecutor retryExecutor = new RetryExecutor(Sleeper.NO_OP);
        JustDoWorkLLMService service = new JustDoWorkLLMService(props, objectMapper, restClient, retryExecutor);

        assertThatThrownBy(() -> service.generate("System prompt", "User prompt"))
                .isInstanceOf(CeoAnalysisException.class)
                .hasMessageContaining("JustDoWork request failed");
        mockServer.verify();
    }

    @Test
    void realMode_hasNoDeterministicFallbackWhenProviderFails() {
        props.setMode(AiExecutionMode.REAL);
        props.getJustdowork().setApiKey(""); // Missing key

        JustDoWorkLLMService service = new JustDoWorkLLMService(props, objectMapper);
        CeoAgent agent = new CeoAgent(service, props);

        Startup startup = new Startup();
        startup.setId(1L);
        startup.setName("Test Startup");
        startup.setOriginalIdea("Test Idea");
        StartupContext ctx = new StartupContext(startup);

        CeoAnalysisOutcome outcome = agent.runAnalysis(ctx);

        assertThat(outcome.failed()).isTrue();
        assertThat(outcome.provider()).isEqualTo("unavailable");
        assertThat(ctx.getStartup().isCeoAnalysisFailed()).isTrue();
        assertThat(ctx.getStartup().getExecutiveSummary()).isNull();
    }

    @Test
    void missingApiKey_producesExplicitProviderFailure() {
        props.getJustdowork().setApiKey(""); // Empty key
        JustDoWorkLLMService service = new JustDoWorkLLMService(props, objectMapper);

        assertThat(service.isRealProvider()).isFalse();
        assertThatThrownBy(() -> service.generate("System prompt", "User prompt"))
                .isInstanceOf(CeoAnalysisException.class)
                .hasMessageContaining("JustDoWork API key is not configured (set JUSTDOWORK_API_KEY)");
    }
}
