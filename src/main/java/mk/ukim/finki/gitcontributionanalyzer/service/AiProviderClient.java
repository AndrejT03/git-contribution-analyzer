package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.model.AiProviderSelection;

public interface AiProviderClient {
    Completion complete(AiProviderSelection providerSelection, String apiKey, String prompt);

    record Completion(String content, String finishReason) {    }
}