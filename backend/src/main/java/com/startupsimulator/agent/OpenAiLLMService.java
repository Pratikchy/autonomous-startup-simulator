package com.startupsimulator.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupsimulator.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-backed {@link LLMService} (Chat Completions API). Registered as the
 * sole {@link LLMService} only when {@code app.ai.provider=openai}; otherwise
 * {@link MockLLMService} is used, so exactly one implementation exists and no
 * {@code @Primary} tie-break is required.
 *
 * <p>The API key is read from configuration (env-driven) and never logged or
 * exposed. Structured calls request {@code response_format=json_object} and the
 * parsed content is deserialized into the requested type. Any transport,
 * provider, or parse problem is surfaced as {@link CeoAnalysisException} so the
 * caller can fail gracefully.
 */
import com.startupsimulator.resilience.RetryExecutor;
import com.startupsimulator.resilience.RetryPolicy;

@Service
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "openai")
public class OpenAiLLMService implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLLMService.class);

    private final LlmProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final RetryExecutor retryExecutor;
    private final RetryPolicy retryPolicy;

    public OpenAiLLMService(LlmProperties props, ObjectMapper objectMapper) {
        this(props, objectMapper, new RetryExecutor());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OpenAiLLMService(LlmProperties props, ObjectMapper objectMapper, RetryExecutor retryExecutor) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.retryExecutor = retryExecutor != null ? retryExecutor : new RetryExecutor();
        this.retryPolicy = props != null && props.getResilience() != null
                ? props.getResilience().toRetryPolicy()
                : new RetryPolicy();
        LlmProperties.OpenAi cfg = props.getOpenai();
        int timeoutSeconds = props != null ? props.getRequestTimeoutSeconds() : 0;
        this.restClient = RestClient.builder()
                .requestFactory(LlmHttpClients.timedRequestFactory(timeoutSeconds))
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + safeKey(cfg.getApiKey()))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("OpenAiLLMService initialised (model={}, baseUrl={}, apiKeyPresent={}, maxAttempts={})",
                cfg.getModel(), cfg.getBaseUrl(), cfg.getApiKey() != null && !cfg.getApiKey().isBlank(),
                retryPolicy.getMaxAttempts());
    }

    /**
     * Package-private constructor for unit testing with a pre-configured RestClient (e.g. backed by MockRestServiceServer).
     */
    OpenAiLLMService(LlmProperties props, ObjectMapper objectMapper, RestClient restClient, RetryExecutor retryExecutor) {
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
        // Phrasing passthrough — mock department agents call this and must NOT
        // trigger paid API calls (cost control: one LLM call per simulation).
        return user == null ? "" : user.trim();
    }

    @Override
    public String generate(String system, String user) {
        return retryExecutor.execute("OpenAiLLMService.generate", () -> chat(system, user, false), retryPolicy);
    }

    @Override
    public <T> T generateStructured(String system, String user, Class<T> type) {
        return retryExecutor.execute("OpenAiLLMService.generateStructured", () -> {
            String content = chat(system, user, true);
            try {
                return objectMapper.readValue(content, type);
            } catch (Exception e) {
                throw new CeoAnalysisException("Failed to parse structured LLM response as " + type.getSimpleName(), e);
            }
        }, retryPolicy);
    }


    /** Make a single Chat Completions call and return the assistant message text. */
    private String chat(String system, String user, boolean json) {
        if (props.getOpenai().getApiKey() == null || props.getOpenai().getApiKey().isBlank()) {
            throw new CeoAnalysisException("OpenAI API key is not configured (set OPENAI_API_KEY).");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getOpenai().getModel());
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
            log.info("LLM call ok (provider=openai, model={}, latencyMs={}, chars={})",
                    props.getOpenai().getModel(), latency, content == null ? 0 : content.length());
            return content;
        } catch (CeoAnalysisException e) {
            throw e;
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - started;
            // Never log the key or the full prompt; capture exception type, message, and HTTP status where available.
            String exceptionType = e.getClass().getSimpleName();
            String exceptionMessage = e.getMessage() != null ? e.getMessage() : "";

            // Extract HTTP status code if available from Spring's HTTP exceptions
            String httpStatus = "";
            if (e instanceof org.springframework.web.client.HttpStatusCodeException) {
                org.springframework.web.client.HttpStatusCodeException httpEx =
                    (org.springframework.web.client.HttpStatusCodeException) e;
                httpStatus = ", httpStatus=" + httpEx.getStatusCode().value();
            }

            // Build safe stack trace: top frames only, no exception messages
            String stackInfo = buildSafeStackTrace(e);

            log.warn("LLM call failed (provider=openai, model={}, latencyMs={}{}): {} - {}{}",
                    props.getOpenai().getModel(), latency, httpStatus, exceptionType,
                    sanitizeErrorMessage(exceptionMessage), stackInfo);
            throw new CeoAnalysisException("OpenAI request failed: " + exceptionType, e);
        }
    }

    private String extractContent(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);

            // Detect OpenRouter error envelope returned at HTTP 200
            if (root.has("error")) {
                JsonNode errorNode = root.get("error");
                String message = errorNode.path("message").asText(null);
                if (message == null || message.isBlank()) {
                    message = errorNode.path("error").isTextual()
                            ? errorNode.get("error").asText()
                            : "OpenRouter returned an error envelope";
                }
                String code = errorNode.path("code").asText(null);
                String detail = code != null ? " (code=" + code + ")" : "";
                throw new CeoAnalysisException("OpenRouter error" + detail + ": " + message);
            }

            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                String model = root.path("model").asText(null);
                throw new CeoAnalysisException("OpenAI response has no choices"
                        + (model != null ? " (model=" + model + ")" : ""));
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode messageNode = firstChoice.path("message");

            // Surface finish_reason for diagnostics — never guess what went wrong
            String finishReason = firstChoice.path("finish_reason").asText(null);

            JsonNode contentNode = messageNode.path("content");

            // Handle array-based content (OpenRouter free-tier models)
            if (contentNode.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode block : contentNode) {
                    if ("text".equals(block.path("type").asText(null))) {
                        String text = block.path("text").asText(null);
                        if (text != null && !text.isBlank()) {
                            sb.append(text);
                        }
                    }
                }
                if (sb.isEmpty()) {
                    throw emptyCompletion("content array", finishReason);
                }
                return sb.toString();
            }

            // Plain-text content
            String text = contentNode.asText(null);

            if (text == null || text.isBlank()) {
                throw emptyCompletion("null/blank content", finishReason);
            }

            // Strip markdown fences if the content is JSON wrapped in ```json ... ```
            String trimmed = text.trim();
            if ((trimmed.startsWith("```json") || trimmed.startsWith("```")) && trimmed.endsWith("```")) {
                int fenceEnd = trimmed.indexOf('\n');
                if (fenceEnd > 0) {
                    String inner = trimmed.substring(fenceEnd + 1, trimmed.length() - 3).trim();
                    // Verify it looks like JSON before returning it
                    if (inner.startsWith("{")) {
                        return inner;
                    }
                }
            }

            return text;
        } catch (CeoAnalysisException e) {
            throw e;
        } catch (Exception e) {
            throw new CeoAnalysisException("Malformed OpenAI response envelope.", e);
        }
    }

    private static CeoAnalysisException emptyCompletion(String reason, String finishReason) {
        String msg = "OpenAI returned an empty completion (" + reason + ")";
        if (finishReason != null && !finishReason.isBlank()) {
            msg += "; finish_reason=" + finishReason;
            // Distinguish truncation from refusals / safety filters for operator visibility
            if ("length".equals(finishReason)) {
                msg += " (response truncated — max tokens reached or context full)";
            } else if ("content_filter".equals(finishReason) || "safety".equals(finishReason)) {
                msg += " (content filtered or refused)";
            }
        }
        return new CeoAnalysisException(msg);
    }

    private static String safeKey(String key) {
        return key == null ? "" : key;
    }

    /**
     * Sanitizes error messages to prevent accidental logging of API keys or sensitive data.
     * Removes bearer tokens, OpenAI API keys, and truncates long messages.
     */
    private static String sanitizeErrorMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "(no message)";
        }
        // Remove bearer tokens that might appear in error messages
        String sanitized = message.replaceAll("Bearer [A-Za-z0-9_-]+", "Bearer [REDACTED]");
        // Remove OpenAI API keys (sk-proj-, sk-*, etc.)
        sanitized = sanitized.replaceAll("sk-[a-zA-Z0-9_-]+", "sk-[REDACTED]");
        // Truncate very long messages
        if (sanitized.length() > 500) {
            return sanitized.substring(0, 497) + "...";
        }
        return sanitized;
    }

    /**
     * Builds a safe stack trace summary containing only stack frame locations (class, method, file, line).
     * Does NOT include exception messages, cause messages, or any runtime data that could contain secrets.
     * Limits to top 5 frames to keep logs concise while preserving diagnostic value.
     */
    private static String buildSafeStackTrace(Exception e) {
        StackTraceElement[] trace = e.getStackTrace();
        if (trace == null || trace.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(5, trace.length);
        for (int i = 0; i < limit; i++) {
            sb.append("\n  at ").append(trace[i].toString());
        }
        if (trace.length > limit) {
            sb.append("\n  ... ").append(trace.length - limit).append(" more");
        }
        return sb.toString();
    }

    @Override
    public String provider() {
        return "openai";
    }

    @Override
    public boolean isRealProvider() {
        return props.getOpenai().getApiKey() != null && !props.getOpenai().getApiKey().isBlank();
    }

    /** Exposed for logging/diagnostics without leaking the key. */
    public Duration requestTimeout() {
        return Duration.ofSeconds(props.getRequestTimeoutSeconds());
    }
}
