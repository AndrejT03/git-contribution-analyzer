package mk.ukim.finki.gitcontributionanalyzer.model;
import mk.ukim.finki.gitcontributionanalyzer.enums.AiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

class AiProviderSelectionTest {

    private static final Pattern MODEL_OPTION = Pattern.compile(
            "<option\\s+value=\"([^\"]+::[^\"]+)\""
    );

    @Test
    void parsesAllFiftyTwoModelsAcrossAllElevenProviders() {
        assertThat(AiProvider.values()).hasSize(11);
        assertThat(AiProviderSelection.supportedValues()).hasSize(51);

        assertThat(AiProviderSelection.supportedValues())
                .allSatisfy(value -> {
                    AiProviderSelection selection = AiProviderSelection.parse(value);
                    assertThat(selection.namespacedModel()).isEqualTo(value);
                    assertThat(selection.provider().supports(selection.model())).isTrue();
                })
                .contains(
                        "google::gemini-3.8-flash",
                        "google::gemini-3.6-flash",
                        "anthropic::claude-fable-5-1",
                        "openrouter::z-ai/glm-5.3-flash",
                        "groq::qwen/qwen3.8-27b",
                        "together::moonshotai/Kimi-K3",
                        "cerebras::gemma-4-31b"
                );
    }

    @Test
    void backendCatalogExactlyMatchesTheDropdownContract() throws IOException {
        String form = new ClassPathResource("templates/index.html")
                .getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = MODEL_OPTION.matcher(form);
        Set<String> formModels = new LinkedHashSet<>();
        int optionCount = 0;
        while (matcher.find()) {
            optionCount++;
            formModels.add(matcher.group(1));
        }

        assertThat(optionCount).isEqualTo(51);
        assertThat(formModels)
                .hasSize(51)
                .containsExactlyElementsOf(AiProviderSelection.supportedValues());
    }

    @Test
    void preservesProviderSpecificModelPunctuation() {
        AiProviderSelection selection = AiProviderSelection.parse(
                "  openrouter::~anthropic/claude-sonnet-latest  "
        );

        assertThat(selection.provider()).isEqualTo(AiProvider.OPENROUTER);
        assertThat(selection.model()).isEqualTo("~anthropic/claude-sonnet-latest");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "openai",
            "openai::",
            "::gpt-5.6-terra",
            "unknown::gpt-5.6-terra",
            "openai::claude-sonnet-5",
            "openai::gpt-5.6-terra::https://attacker.example"
    })
    void rejectsUnknownOrMalformedSelections(String value) {
        assertThat(AiProviderSelection.tryParse(value)).isEmpty();
        assertThatThrownBy(() -> AiProviderSelection.parse(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The selected AI model is not supported.");
    }

    @Test
    void rejectsNullSelection() {
        assertThat(AiProviderSelection.tryParse(null)).isEmpty();
    }

    @Test
    void usesOnlyFixedHttpsProviderEndpoints() {
        assertThat(Arrays.asList(AiProvider.values()))
                .allSatisfy(provider -> {
                    assertThat(provider.chatCompletionsEndpoint().getScheme()).isEqualTo("https");
                    assertThat(provider.chatCompletionsEndpoint().getHost()).isNotBlank();
                    assertThat(provider.chatCompletionsEndpoint().getUserInfo()).isNull();
                    assertThat(provider.chatCompletionsEndpoint().getQuery()).isNull();
                });
    }

    @Test
    void keepsTheAuditedProviderHostsAndPathsFixed() {
        assertThat(Arrays.asList(AiProvider.values()))
                .extracting(
                        AiProvider::namespace,
                        provider -> provider.chatCompletionsEndpoint().toString()
                )
                .containsExactly(
                        tuple("google", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"),
                        tuple("openai", "https://api.openai.com/v1/chat/completions"),
                        tuple("anthropic", "https://api.anthropic.com/v1/chat/completions"),
                        tuple("openrouter", "https://openrouter.ai/api/v1/chat/completions"),
                        tuple("xai", "https://api.x.ai/v1/chat/completions"),
                        tuple("deepseek", "https://api.deepseek.com/chat/completions"),
                        tuple("mistral", "https://api.mistral.ai/v1/chat/completions"),
                        tuple("groq", "https://api.groq.com/openai/v1/chat/completions"),
                        tuple("together", "https://api.together.ai/v1/chat/completions"),
                        tuple("huggingface", "https://router.huggingface.co/v1/chat/completions"),
                        tuple("cerebras", "https://api.cerebras.ai/v1/chat/completions")
                );
    }
}