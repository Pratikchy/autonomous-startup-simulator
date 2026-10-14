package com.startupsimulator.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupsimulator.config.AiExecutionMode;
import com.startupsimulator.config.LlmProperties;
import com.startupsimulator.resilience.RetryExecutor;
import com.startupsimulator.resilience.Sleeper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests for OpenAiLLMService, focused on {@code extractContent} robustness
 * against OpenRouter's non-standard response shapes: array-based content blocks,
 * HTTP 200 error envelopes, empty/missing choices, and malformed JSON.
 *
 * <p>Uses MockRestServiceServer to intercept the HTTP layer, letting us test
 * response parsing in isolation without a live endpoint.
 */
class OpenAiLLMServiceTest {

    private ObjectMapper objectMapper;
    private LlmProperties props;
    private RestClient restClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        props = new LlmProperties();
        props.setProvider("openai");
        props.setMode(AiExecutionMode.REAL);
        props.setRequestTimeoutSeconds(30);

        LlmProperties.OpenAi openAi = props.getOpenai();
        openAi.setApiKey("test-openai-key-456");
        openAi.setBaseUrl("https://api.openai.com/v1");
        openAi.setModel("gpt-4o-mini");
        // Cap retries at 1 so non-2xx tests don't exceed the mock expectation limit
        props.getResilience().setMaxAttempts(1);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(openAi.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + openAi.getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    private OpenAiLLMService newService() {
        return new OpenAiLLMService(props, objectMapper, restClient, new RetryExecutor(Sleeper.NO_OP));
    }

    @Test
    @DisplayName("Plain-text message.content is returned as-is")
    void plainTextContentReturned() {
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Build a prototype.\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(newService().generate("system", "user")).isEqualTo("Build a prototype.");
        mockServer.verify();
    }

    @Test
    @DisplayName("Array-based content blocks are concatenated into a single string")
    void arrayContentBlocksConcatenated() {
        // OpenRouter free-tier models return content as an array of text blocks
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":[
                            {"type":"text","text":"Step 1: Define MVP scope. "},
                            {"type":"text","text":"Step 2: Build in public."}
                        ]}}]}""", MediaType.APPLICATION_JSON));

        assertThat(newService().generate("system", "user"))
                .isEqualTo("Step 1: Define MVP scope. Step 2: Build in public.");
        mockServer.verify();
    }

    @Test
    @DisplayName("Empty content array throws descriptive CeoAnalysisException")
    void emptyContentArrayThrows() {
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":[]}}]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> newService().generate("system", "user"))
                .isInstanceOf(CeoAnalysisException.class)
                .hasMessageContaining("empty completion");
        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 200 error envelope from OpenRouter throws descriptive exception")
    void openRouterErrorEnvelopeAtHttp200Throws() {
        // OpenRouter returns HTTP 200 with an error object when routing fails
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"error":{"message":"Model 'openrouter/free' not found","code":"invalid_model"}}""",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> newService().generate("system", "user"))
                .isInstanceOf(CeoAnalysisException.class)
                .hasMessageContaining("Model 'openrouter/free' not found");
        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 200 error envelope with nested error.message throws descriptive exception")
    void openRouterNestedErrorEnvelopeThrows() {
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"error":{"message":"Insufficient credits","code":"billing"}}""",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> newService().generate("system", "user"))
                .isInstanceOf(CeoAnalysisException.class)
                .hasMessageContaining("Insufficient credits");
        mockServer.verify();
    }

    @Test
    @DisplayName("Non-2xx HTTP status throws CeoAnalysisException")
    void non2xxStatusThrows() {
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        CeoAnalysisException thrown = null;
        try {
            newService().generate("system", "user");
        } catch (CeoAnalysisException e) {
            thrown = e;
        }
        assertThat(thrown).as("CeoAnalysisException should be thrown for non-2xx response").isNotNull();
        assertThat(thrown.getMessage()).contains("OpenAI request failed");
        mockServer.verify();
    }

    @Test
    @DisplayName("API key is never logged or exposed in exceptions")
    void apiKeyNotExposedInExceptions() {
        props.getOpenai().setApiKey("super-secret-openai-key-999");
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CeoAnalysisException thrown = null;
        try {
            newService().generate("system", "user");
        } catch (CeoAnalysisException e) {
            thrown = e;
        }
        assertThat(thrown).as("CeoAnalysisException should be thrown for 401").isNotNull();
        assertThat(thrown.getMessage()).doesNotContain("super-secret-openai-key-999");
        assertThat(thrown.getMessage()).doesNotContain("openai-key-999");
        mockServer.verify();
    }

    @Test
    @DisplayName("JSON wrapped in markdown fences is unwrapped and returned")
    void markdownJsonFenceIsUnwrapped() {
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"```json\\n{\\"positioning\\":\\"B2B SaaS copilot\\"}\\n```"}}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(newService().generate("system", "user"))
                .isEqualTo("{\"positioning\":\"B2B SaaS copilot\"}");
        mockServer.verify();
    }

    @Test
    @DisplayName("Short non-JSON text content is returned as-is without crashing")
    void shortNonJsonTextReturned() {
        // 17-char raw text — the symptom in the real crash report
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Build it fast.\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(newService().generate("system", "user")).isEqualTo("Build it fast.");
        mockServer.verify();
    }

    @Test
    @DisplayName("Finish reason 'length' is surfaced in the empty-completion message")
    void finishReasonLengthSurface() {
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"finish_reason\":\"length\"}]}",
                        MediaType.APPLICATION_JSON));

        CeoAnalysisException thrown = null;
        try {
            newService().generate("system", "user");
        } catch (CeoAnalysisException e) {
            thrown = e;
        }
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains("finish_reason=length");
        assertThat(thrown.getMessage()).contains("truncated");
        mockServer.verify();
    }

    @Test
    @DisplayName("Finish reason 'content_filter' is surfaced in the empty-completion message")
    void finishReasonContentFilterSurface() {
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null},\"finish_reason\":\"content_filter\"}]}",
                        MediaType.APPLICATION_JSON));

        CeoAnalysisException thrown = null;
        try {
            newService().generate("system", "user");
        } catch (CeoAnalysisException e) {
            thrown = e;
        }
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains("finish_reason=content_filter");
        assertThat(thrown.getMessage()).contains("filtered or refused");
        mockServer.verify();
    }

    @Test
    @DisplayName("Empty choices includes the model name in the exception message")
    void emptyChoicesIncludesModelInMessage() {
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess("{\"model\":\"openrouter/free\",\"choices\":[]}", MediaType.APPLICATION_JSON));

        CeoAnalysisException thrown = null;
        try {
            newService().generate("system", "user");
        } catch (CeoAnalysisException e) {
            thrown = e;
        }
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains("no choices");
        assertThat(thrown.getMessage()).contains("openrouter/free");
        mockServer.verify();
    }

    @Test
    @DisplayName("Valid structured JSON response parses as MarketingAnalysisResponse")
    void validStructuredJsonParses() {
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":
                        "{\\"positioning\\":\\"B2B SaaS copilot\\",\\"competitorAnalysis\\":\\"Incumbent tools are slow.\\",\\"pricingStrategy\\":\\"Tiered SaaS.\\",\\"channels\\":[\\"SEO\\",\\"cold outreach\\"],\\"goToMarket\\":\\"Beachhead then expand.\\",\\"targetAudience\\":\\"Engineering leads\\",\\"valueProposition\\":\\"Ship faster.\\",\\"businessModel\\":\\"Subscription.\\",\\"risks\\":[\\"Churn risk\\"]}"}}]}""",
                        MediaType.APPLICATION_JSON));

        MarketingAnalysisResponse resp = newService()
                .generateStructured("system", "user", MarketingAnalysisResponse.class);

        assertThat(resp.positioning()).isEqualTo("B2B SaaS copilot");
        assertThat(resp.pricingStrategy()).isEqualTo("Tiered SaaS.");
        assertThat(resp.channels()).containsExactly("SEO", "cold outreach");
        assertThat(resp.isValid()).isTrue();
        mockServer.verify();
    }

    // --- Exact live-failure path: openrouter/free returns 17 chars of non-JSON ---

    @Test
    @DisplayName("17-char non-JSON completion fails structured parse (never fabricated) ")
    void shortNonJsonFailsStructuredParse() {
        // "Build it faster.." is 17 chars of prose, not the required JSON object.
        // This is the exact DeveloperAnalysisResponse failure from the live run.
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Build it faster..\"},\"finish_reason\":\"stop\"}]}",
                        MediaType.APPLICATION_JSON));

        CeoAnalysisException thrown = null;
        try {
            newService().generateStructured("system", "user", DeveloperAnalysisResponse.class);
        } catch (CeoAnalysisException e) {
            thrown = e;
        }
        assertThat(thrown).as("non-JSON must surface as a parse failure, not a fabricated analysis").isNotNull();
        assertThat(thrown.getMessage()).contains("Failed to parse structured LLM response");
        assertThat(thrown.getMessage()).contains("DeveloperAnalysisResponse");
        mockServer.verify();
    }

    @Test
    @DisplayName("Empty content with finish_reason=stop is the recovery symptom and throws clearly")
    void emptyContentFinishStopThrows() {
        // The recovery call in the live run returned chars=0 with finish_reason=stop.
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"finish_reason\":\"stop\"}]}",
                        MediaType.APPLICATION_JSON));

        CeoAnalysisException thrown = null;
        try {
            newService().generate("system", "user");
        } catch (CeoAnalysisException e) {
            thrown = e;
        }
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).contains("empty completion");
        assertThat(thrown.getMessage()).contains("finish_reason=stop");
        mockServer.verify();
    }
}
