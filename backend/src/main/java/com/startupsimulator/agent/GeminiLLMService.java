package com.startupsimulator.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupsimulator.config.LlmProperties;
import com.startupsimulator.resilience.RetryExecutor;
import com.startupsimulator.resilience.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini-backed {@link LLMService} (Google Generative AI REST API v1beta).
 * Registered as the sole {@link LLMService} when {@code app.ai.provider=gemini}.
 * Uses model {@code gemini-3.8-flash} at base URL {@code https://generativelanguage.googleapis.com/v1beta}.
 * Authenticates using the {@code x-goog-api-key} header.
 */
@Service
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "gemini")
public class GeminiLLMService implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(GeminiLLMService.class);

    private final LlmProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final RetryExecutor retryExecutor;
    private final RetryPolicy retryPolicy;

    public GeminiLLMService(LlmProperties props, ObjectMapper objectMapper) {
        this(props, objectMapper, new RetryExecutor());
    }

    @Autowired
    public GeminiLLMService(LlmProperties props, ObjectMapper objectMapper, RetryExecutor retryExecutor) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.retryExecutor = retryExecutor != null ? retryExecutor : new RetryExecutor();
        this.retryPolicy = props != null && props.getResilience() != null
                ? props.getResilience().toRetryPolicy()
                : new RetryPolicy();
        LlmProperties.Gemini cfg = props != null ? props.getGemini() : new LlmProperties.Gemini();
        int timeoutSeconds = props != null ? props.getRequestTimeoutSeconds() : 0;
        this.restClient = RestClient.builder()
                .requestFactory(LlmHttpClients.timedRequestFactory(timeoutSeconds))
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader("x-goog-api-key", safeKey(cfg.getApiKey()))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("GeminiLLMService initialised (model={}, baseUrl={}, apiKeyPresent={}, maxAttempts={})",
                cfg.getModel(), cfg.getBaseUrl(), cfg.getApiKey() != null && !cfg.getApiKey().isBlank(),
                retryPolicy.getMaxAttempts());
    }

    /**
     * Package-private constructor for unit testing with a pre-configured RestClient (e.g. backed by MockRestServiceServer).
     */
    GeminiLLMService(LlmProperties props, ObjectMapper objectMapper, RestClient restClient, RetryExecutor retryExecutor) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.retryExecutor = retryExecutor != null ? retryExecutor : new RetryExecutor();
        this.retryPolicy = props != null && props.getResilience() != null
                ? props.getResilience().toRetryPolicy()
                : new RetryPolicy();
    }

    @Override
    public String complete(String system, String user) {
        return user == null ? "" : user.trim();
    }

    @Override
    public String generate(String system, String user) {
        return retryExecutor.execute("GeminiLLMService.generate", () -> chat(system, user, false), retryPolicy);
    }

    @Override
    public <T> T generateStructured(String system, String user, Class<T> type) {
        return retryExecutor.execute("GeminiLLMService.generateStructured", () -> {
            String content = chat(system, user, true);
            try {
                return objectMapper.readValue(content, type);
            } catch (Exception e) {
                throw new CeoAnalysisException("Failed to parse structured LLM response as " + type.getSimpleName(), e);
            }
        }, retryPolicy);
    }

    private String chat(String system, String user, boolean json) {
        LlmProperties.Gemini cfg = props.getGemini();
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new CeoAnalysisException("Gemini API key is not configured (set GEMINI_API_KEY).");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        if (system != null && !system.isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", system))));
        }
        body.put("contents", List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", user == null ? "" : user)))
        ));

        if (json) {
            body.put("generationConfig", Map.of("responseMimeType", "application/json"));
        }

        String uri = "/models/" + cfg.getModel() + ":generateContent";
        long started = System.currentTimeMillis();
        try {
            String raw = restClient.post()
                    .uri(uri)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            long latency = System.currentTimeMillis() - started;
            String content = extractContent(raw);
            log.info("LLM call ok (provider=gemini, model={}, latencyMs={}, chars={})",
                    cfg.getModel(), latency, content == null ? 0 : content.length());
            if (content == null || content.isBlank()) {
                throw new CeoAnalysisException("Gemini returned an empty completion.");
            }
            return content;
        } catch (CeoAnalysisException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            long latency = System.currentTimeMillis() - started;
            int statusCode = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            log.warn("LLM call failed (provider=gemini, path={}, status={}, latencyMs={}, responseBody={})",
                    uri, statusCode, latency, responseBody);
            throw new CeoAnalysisException("Gemini request failed with HTTP " + statusCode + ": " + responseBody, e);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - started;
            log.warn("LLM call failed (provider=gemini, path={}, latencyMs={}): {}",
                    uri, latency, e.getClass().getSimpleName());
            throw new CeoAnalysisException("Gemini request failed: " + e.getClass().getSimpleName(), e);
        }
    }

    private String extractContent(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode candidate = candidates.get(0);
                JsonNode content = candidate.path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode part : parts) {
                        if (part.has("text") && !part.path("text").isNull()) {
                            sb.append(part.path("text").asText());
                        }
                    }
                    if (sb.length() > 0) {
                        return sb.toString();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            throw new CeoAnalysisException("Malformed Gemini response envelope.", e);
        }
    }

    private static String safeKey(String key) {
        return key == null ? "" : key;
    }

    @Override
    public String provider() {
        return "gemini";
    }

    @Override
    public boolean isRealProvider() {
        return props.getGemini().getApiKey() != null && !props.getGemini().getApiKey().isBlank();
    }

    public Duration requestTimeout() {
        return Duration.ofSeconds(props.getRequestTimeoutSeconds());
    }
}
