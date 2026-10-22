package com.startupsimulator.agent;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Builds a timeout-bounded {@link ClientHttpRequestFactory} for the real LLM
 * providers (OpenAI, Gemini, JustDoWork).
 *
 * <p>The three providers previously built their {@code RestClient} with no
 * request factory, so a stalled provider response (connection accepted but body
 * never completed) blocked the calling thread indefinitely — in REAL mode that
 * parks the single orchestration loop thread forever. Applying both a connect
 * and a read timeout turns such a stall into a {@code SocketTimeoutException},
 * which {@code FailureClassifier} already treats as RETRYABLE_TRANSIENT, so the
 * existing retry/backoff and explicit failure/recovery path engage normally.
 *
 * <p>The timeout value comes from {@code LlmProperties.requestTimeoutSeconds};
 * this helper only converts it overflow-safely and guards against a
 * non-positive (misconfigured) value.
 *
 * <p>Uses {@link Duration}-based timeout methods introduced in Spring Framework
 * 6.1 for compatibility with Spring Boot 3.5+ and Spring Framework 6.2+. The
 * legacy millisecond-based methods can cause {@code IllegalArgumentException} in
 * certain edge cases with modern RestClient implementations.
 */
public final class LlmHttpClients {

    /**
     * Fallback applied ONLY when the configured timeout is non-positive
     * (misconfiguration guard). The operational timeout always comes from
     * {@code LlmProperties.requestTimeoutSeconds}; this is never the normal path.
     */
    static final int FALLBACK_TIMEOUT_SECONDS = 30;

    private LlmHttpClients() {
    }

    /**
     * Convert a seconds timeout to a positive millisecond value, safely:
     * non-positive input falls back to {@link #FALLBACK_TIMEOUT_SECONDS}, and a
     * value large enough to overflow an {@code int} of milliseconds is capped at
     * {@link Integer#MAX_VALUE} (never negative).
     */
    static int resolveTimeoutMillis(int timeoutSeconds) {
        int effectiveSeconds = timeoutSeconds > 0 ? timeoutSeconds : FALLBACK_TIMEOUT_SECONDS;
        long millis = (long) effectiveSeconds * 1000L;
        if (millis > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) millis;
    }

    /**
     * A simple blocking request factory with both connect and read timeouts set
     * to the resolved value, so neither connection establishment nor response
     * read can block without bound.
     *
     * <p>Uses Duration-based timeout methods (Spring Framework 6.1+) for
     * compatibility with Spring Boot 3.5+ and Spring Framework 6.2+.
     */
    static ClientHttpRequestFactory timedRequestFactory(int timeoutSeconds) {
        int ms = resolveTimeoutMillis(timeoutSeconds);
        Duration timeout = Duration.ofMillis(ms);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }
}
