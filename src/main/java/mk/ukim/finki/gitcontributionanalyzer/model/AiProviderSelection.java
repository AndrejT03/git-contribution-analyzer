package mk.ukim.finki.gitcontributionanalyzer.model;
import mk.ukim.finki.gitcontributionanalyzer.enums.AiProvider;
import java.util.*;

public record AiProviderSelection(AiProvider provider, String model) {

    private static final String SEPARATOR = "::";
    private static final Set<String> SUPPORTED_VALUES = buildSupportedValues();

    public AiProviderSelection {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        if (!provider.supports(model)) {
            throw new IllegalArgumentException("The selected AI model is not supported.");
        }
    }

    public static AiProviderSelection parse(String value) {
        return tryParse(value)
                .orElseThrow(() -> new IllegalArgumentException("The selected AI model is not supported."));
    }

    public static Optional<AiProviderSelection> tryParse(String value) {
        if (value == null) {
            return Optional.empty();
        }

        String normalized = value.strip();
        int separatorIndex = normalized.indexOf(SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex + SEPARATOR.length() >= normalized.length()) {
            return Optional.empty();
        }

        String namespace = normalized.substring(0, separatorIndex);
        String model = normalized.substring(separatorIndex + SEPARATOR.length());
        return AiProvider.fromNamespace(namespace)
                .filter(provider -> provider.supports(model))
                .map(provider -> new AiProviderSelection(provider, model));
    }

    public static boolean isSupported(String value) {
        return tryParse(value).isPresent();
    }

    public static Set<String> supportedValues() {
        return SUPPORTED_VALUES;
    }

    public String namespacedModel() {
        return provider.namespace() + SEPARATOR + model;
    }

    private static Set<String> buildSupportedValues() {
        Set<String> values = new LinkedHashSet<>();
        Arrays.stream(AiProvider.values()).forEach(provider ->
                provider.supportedModels().forEach(model ->
                        values.add(provider.namespace() + SEPARATOR + model)));
        return Collections.unmodifiableSet(values);
    }
}