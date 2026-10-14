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
 * JustDoWork-backed {@link LLMService} (OpenAI-compatible Chat Completions API gateway).
 * Registered as the sole {@link LLMService} when {@code app.ai.provider=justdowork} (default).
 * Uses model {@code claude-opus-4-8} at base URL {@code https://api.justwoker.icu/v1}.
 */
@Service
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "justdowork", matchIfMissing = true)
public class JustDoWorkLLMService implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(JustDoWorkLLMService.class);

    private final LlmProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final RetryExecutor retryExecutor;
    private final RetryPolicy retryPolicy;

    public JustDoWorkLLMService(LlmProperties props, ObjectMapper objectMapper) {
        this(props, objectMapper, new RetryExecutor());
    }

    @Autowired
    public JustDoWorkLLMService(LlmProperties props, ObjectMapper objectMapper, RetryExecutor retryExecutor) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.retryExecutor = retryExecutor != null ? retryExecutor : new RetryExecutor();
        this.retryPolicy = props != null && props.getResilience() != null
                ? props.getResilience().toRetryPolicy()
                : new RetryPolicy();
        LlmProperties.JustDoWork cfg = props != null ? props.getJustdowork() : new LlmProperties.JustDoWork();
        int timeoutSeconds = props != null ? props.getRequestTimeoutSeconds() : 0;
        this.restClient = RestClient.builder()
                .requestFactory(LlmHttpClients.timedRequestFactory(timeoutSeconds))
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + safeKey(cfg.getApiKey()))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("JustDoWorkLLMService initialised (model={}, baseUrl={}, apiKeyPresent={}, maxAttempts={})",
                cfg.getModel(), cfg.getBaseUrl(), cfg.getApiKey() != null && !cfg.getApiKey().isBlank(),
                retryPolicy.getMaxAttempts());
    }

    /**
     * Package-private constructor for unit testing with a pre-configured RestClient (e.g. backed by MockRestServiceServer).
     */
    JustDoWorkLLMService(LlmProperties props, ObjectMapper objectMapper, RestClient restClient, RetryExecutor retryExecutor) {
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
        return retryExecutor.execute("JustDoWorkLLMService.generate", () -> chat(system, user, false), retryPolicy);
    }

    @Override
    public <T> T generateStructured(String system, String user, Class<T> type) {
        return retryExecutor.execute("JustDoWorkLLMService.generateStructured", () -> {
            String content = chat(system, user, true);
            try {
                return objectMapper.readValue(content, type);
            } catch (Exception e) {
                throw new CeoAnalysisException("Failed to parse structured LLM response as " + type.getSimpleName(), e);
            }
        }, retryPolicy);
    }

    private String chat(String system, String user, boolean json) {
        LlmProperties.JustDoWork cfg = props.getJustdowork();
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new CeoAnalysisException("JustDoWork API key is not configured (set JUSTDOWORK_API_KEY).");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        body.put("temperature", 0.4);
        if (json) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        long started = System.currentTimeMillis();
        try {
            String raw = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            long latency = System.currentTimeMillis() - started;
            String content = extractContent(raw);
            log.info("LLM call ok (provider=justdowork, model={}, latencyMs={}, chars={})",
                    cfg.getModel(), latency, content == null ? 0 : content.length());
            if (content == null || content.isBlank()) {
                throw new CeoAnalysisException("JustDoWork returned an empty completion.");
            }
            return content;
        } catch (CeoAnalysisException e) {
            throw e;
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - started;
            log.warn("LLM call failed (provider=justdowork, model={}, latencyMs={}): {}",
                    cfg.getModel(), latency, e.getClass().getSimpleName());
            throw new CeoAnalysisException("JustDoWork request failed: " + e.getClass().getSimpleName(), e);
        }
    }

    private String extractContent(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode choice = choices.get(0);
                JsonNode message = choice.path("message");
                if (message.has("content") && !message.path("content").isNull()) {
                    JsonNode contentNode = message.get("content");
                    if (contentNode.isTextual()) {
                        return contentNode.asText();
                    } else if (contentNode.isArray() && contentNode.size() > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (JsonNode block : contentNode) {
                            if (block.has("text")) {
                                sb.append(block.path("text").asText());
                            }
                        }
                        return sb.toString();
                    }
                    return contentNode.toString();
                }
            }
            return null;
        } catch (Exception e) {
            throw new CeoAnalysisException("Malformed JustDoWork response envelope.", e);
        }
    }

    private static String safeKey(String key) {
        return key == null ? "" : key;
    }

    @Override
    public String provider() {
        return "justdowork";
    }

    @Override
    public boolean isRealProvider() {
        return props.getJustdowork().getApiKey() != null && !props.getJustdowork().getApiKey().isBlank();
    }

    public Duration requestTimeout() {
        return Duration.ofSeconds(props.getRequestTimeoutSeconds());
    }
}
