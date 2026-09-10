package mk.ukim.finki.gitcontributionanalyzer.enums;
import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum AiProvider {
    GOOGLE(
            "google",
            "Google Gemini",
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            "gemini-3.8-flash",
            "gemini-3.7-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-pro-preview"
    ),
    OPENAI(
            "openai",
            "OpenAI",
            "https://api.openai.com/v1/chat/completions",
            "gpt-5.6-terra",
            "gpt-5.6-sol",
            "gpt-5.6-luna",
            "gpt-6-astra",
            "gpt-5.5",
            "gpt-5.4",
            "gpt-5.4-mini",
            "gpt-5.2",
            "gpt-5.1",
            "gpt-4.1",
            "gpt-4o"
    ),
    ANTHROPIC(
            "anthropic",
            "Anthropic Claude",
            "https://api.anthropic.com/v1/chat/completions",
            "claude-sonnet-5",
            "claude-opus-5",
            "claude-fable-5-1",
            "claude-haiku-4-5-20251001",
            "claude-fable-5",
            "claude-opus-4-8",
            "claude-opus-4-7",
            "claude-opus-4-6",
            "claude-opus-4-5-20251101",
            "claude-sonnet-4-6",
            "claude-sonnet-4-5-20250929"
    ),
    OPENROUTER(
            "openrouter",
            "OpenRouter",
            "https://openrouter.ai/api/v1/chat/completions",
            "openrouter/auto",
            "~openai/gpt-latest",
            "~anthropic/claude-sonnet-latest",
            "~google/gemini-flash-latest",
            "openai/gpt-5.6-luna",
            "z-ai/glm-5.3-flash",
            "deepseek/deepseek-v4-flash-0731",
            "tencent/hy4-preview",
            "xiaomi/mimo-v2.5",
            "tencent/hy3",
            "deepseek/deepseek-v4-flash",
            "minimax/minimax-m3:free",
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "google/gemini-3.7-flash"
    ),
    XAI(
            "xai",
            "xAI",
            "https://api.x.ai/v1/chat/completions",
            "grok-4.6"
    ),
    DEEPSEEK(
            "deepseek",
            "DeepSeek",
            "https://api.deepseek.com/chat/completions",
            "deepseek-v4-pro",
            "deepseek-v4-flash"
    ),
    MISTRAL(
            "mistral",
            "Mistral AI",
            "https://api.mistral.ai/v1/chat/completions",
            "mistral-medium-3-5",
            "mistral-small-2603",
            "mistral-large-2512"
    ),
    GROQ(
            "groq",
            "Groq",
            "https://api.groq.com/openai/v1/chat/completions",
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b",
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "qwen/qwen3.8-27b"
    ),
    TOGETHER(
            "together",
            "Together AI",
            "https://api.together.ai/v1/chat/completions",
            "openai/gpt-oss-120b",
            "Qwen/Qwen3.8-Flash",
            "Qwen/Qwen3.6-Plus",
            "moonshotai/Kimi-K3",
            "zai-org/GLM-5.3-Flash",
            "MiniMaxAI/MiniMax-M3",
            "deepseek-ai/DeepSeek-V4-Pro-0813"
    ),
    HUGGING_FACE(
            "huggingface",
            "Hugging Face",
            "https://router.huggingface.co/v1/chat/completions",
            "openai/gpt-oss-120b:fastest",
            "zai-org/GLM-5.3",
            "zai-org/GLM-5.3-Flash",
            "Qwen/Qwen3-Coder-480B-A35B-Instruct",
            "deepseek-ai/DeepSeek-R1"
    ),
    CEREBRAS(
            "cerebras",
            "Cerebras",
            "https://api.cerebras.ai/v1/chat/completions",
            "gpt-oss-120b",
            "gemma-4-31b"
    );

    private static final Map<String, AiProvider> BY_NAMESPACE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(AiProvider::namespace, Function.identity()));

    private final String namespace;
    private final String displayName;
    private final URI chatCompletionsEndpoint;
    private final Set<String> supportedModels;

    AiProvider(
            String namespace,
            String displayName,
            String chatCompletionsEndpoint,
            String... supportedModels) {
        this.namespace = namespace;
        this.displayName = displayName;
        this.chatCompletionsEndpoint = URI.create(chatCompletionsEndpoint);
        this.supportedModels = Collections.unmodifiableSet(
                new LinkedHashSet<>(Arrays.asList(supportedModels))
        );
    }

    public String namespace() {
        return namespace;
    }

    public String displayName() {
        return displayName;
    }

    public URI chatCompletionsEndpoint() {
        return chatCompletionsEndpoint;
    }

    public Set<String> supportedModels() {
        return supportedModels;
    }

    public boolean supports(String model) {
        return supportedModels.contains(model);
    }

    public static Optional<AiProvider> fromNamespace(String namespace) {
        return Optional.ofNullable(BY_NAMESPACE.get(namespace));
    }
}