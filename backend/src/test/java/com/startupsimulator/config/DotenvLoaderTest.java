package com.startupsimulator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupsimulator.agent.OpenAiLLMService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DotenvLoaderTest {

    private static final String TEST_KEY_1 = "TEST_DOTENV_KEY_ALPHA";
    private static final String TEST_KEY_2 = "TEST_DOTENV_KEY_BETA";
    private static final String TEST_EXISTING = "TEST_DOTENV_EXISTING";

    @AfterEach
    void tearDown() {
        System.clearProperty(TEST_KEY_1);
        System.clearProperty(TEST_KEY_2);
        System.clearProperty(TEST_EXISTING);
    }

    @Test
    void parse_handlesCommentsBlankLinesAndWhitespace() {
        List<String> lines = List.of(
                "",
                "   ",
                "# This is a comment",
                "// Another comment style",
                "  KEY_ONE = value1  ",
                "KEY_TWO=\"quoted value with spaces\"",
                "KEY_THREE='single quoted value'",
                "KEY_FOUR=unquoted_value # inline comment",
                "KEY_FIVE=https://openrouter.ai/api/v1?token=xyz=123",
                "INVALID_NO_EQUALS"
        );

        Map<String, String> parsed = DotenvLoader.parse(lines);

        assertThat(parsed).hasSize(5);
        assertThat(parsed.get("KEY_ONE")).isEqualTo("value1");
        assertThat(parsed.get("KEY_TWO")).isEqualTo("quoted value with spaces");
        assertThat(parsed.get("KEY_THREE")).isEqualTo("single quoted value");
        assertThat(parsed.get("KEY_FOUR")).isEqualTo("unquoted_value");
        assertThat(parsed.get("KEY_FIVE")).isEqualTo("https://openrouter.ai/api/v1?token=xyz=123");
    }

    @Test
    void applyToSystemProperties_neverOverwritesExistingProperty() {
        System.setProperty(TEST_EXISTING, "pre_existing_value");

        Map<String, String> entries = Map.of(
                TEST_EXISTING, "new_env_value",
                TEST_KEY_1, "value_1"
        );

        int applied = DotenvLoader.applyToSystemProperties(entries);

        // Only TEST_KEY_1 should be applied; TEST_EXISTING must be preserved
        assertThat(applied).isEqualTo(1);
        assertThat(System.getProperty(TEST_EXISTING)).isEqualTo("pre_existing_value");
        assertThat(System.getProperty(TEST_KEY_1)).isEqualTo("value_1");
    }

    @Test
    void load_handlesMissingFileGracefully() {
        assertDoesNotThrow(() -> {
            DotenvLoader.load(List.of(Path.of("/non/existent/path/.env")));
        });
    }

    @Test
    void load_populatesSystemPropertiesFromActualFile(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.write(envFile, List.of(
                "AI_PROVIDER=openai",
                "AI_MODE=REAL",
                TEST_KEY_1 + "=\"hello-world\"",
                TEST_KEY_2 + "=42"
        ));

        DotenvLoader.load(List.of(envFile));

        assertThat(System.getProperty(TEST_KEY_1)).isEqualTo("hello-world");
        assertThat(System.getProperty(TEST_KEY_2)).isEqualTo("42");
    }

    @Test
    void findCandidateFiles_containsStandardLocations() {
        List<Path> candidates = DotenvLoader.findCandidateFiles();
        assertThat(candidates).isNotEmpty();
        assertThat(candidates).contains(Path.of(".env"), Path.of("../.env"), Path.of("backend/.env"));
    }

    @Test
    void openAiProvider_isRecognizedAsRealProviderWhenConfigured() {
        LlmProperties props = new LlmProperties();
        props.setProvider("openai");
        props.setMode(AiExecutionMode.REAL);
        props.getOpenai().setApiKey("dummy-key");
        props.getOpenai().setBaseUrl("https://openrouter.ai/api/v1");
        props.getOpenai().setModel("openai/gpt-4o-mini");

        OpenAiLLMService service = new OpenAiLLMService(props, new com.fasterxml.jackson.databind.ObjectMapper());

        assertThat(service.provider()).isEqualTo("openai");
        assertThat(service.isRealProvider()).isTrue();
    }

    @Test
    void openAiProvider_recognizesOpenRouterEnvConfiguration(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.write(envFile, List.of(
                "AI_PROVIDER=openai",
                "AI_MODE=REAL",
                "OPENAI_BASE_URL=https://openrouter.ai/api/v1",
                "OPENAI_MODEL=google/gemini-2.5-flash",
                "OPENAI_API_KEY=test-masked-key"
        ));

        DotenvLoader.load(List.of(envFile));

        try {
            assertThat(System.getProperty("AI_PROVIDER")).isEqualTo("openai");
            assertThat(System.getProperty("AI_MODE")).isEqualTo("REAL");
            assertThat(System.getProperty("OPENAI_BASE_URL")).isEqualTo("https://openrouter.ai/api/v1");
            assertThat(System.getProperty("OPENAI_MODEL")).isEqualTo("google/gemini-2.5-flash");
            assertThat(System.getProperty("OPENAI_API_KEY")).isEqualTo("test-masked-key");

            LlmProperties props = new LlmProperties();
            props.setProvider(System.getProperty("AI_PROVIDER"));
            props.setMode(AiExecutionMode.valueOf(System.getProperty("AI_MODE")));
            props.getOpenai().setBaseUrl(System.getProperty("OPENAI_BASE_URL"));
            props.getOpenai().setModel(System.getProperty("OPENAI_MODEL"));
            props.getOpenai().setApiKey(System.getProperty("OPENAI_API_KEY"));

            OpenAiLLMService service = new OpenAiLLMService(props, new ObjectMapper());
            assertThat(service.provider()).isEqualTo("openai");
            assertThat(service.isRealProvider()).isTrue();
        } finally {
            System.clearProperty("AI_PROVIDER");
            System.clearProperty("AI_MODE");
            System.clearProperty("OPENAI_BASE_URL");
            System.clearProperty("OPENAI_MODEL");
            System.clearProperty("OPENAI_API_KEY");
        }
    }
}
