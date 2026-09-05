package mk.ukim.finki.gitcontributionanalyzer.service.impl;
import jakarta.validation.Validator;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.dto.*;
import mk.ukim.finki.gitcontributionanalyzer.enums.AiProvider;
import mk.ukim.finki.gitcontributionanalyzer.enums.CommitCategory;
import mk.ukim.finki.gitcontributionanalyzer.enums.ContributionLevel;
import mk.ukim.finki.gitcontributionanalyzer.enums.AiFailureReason;
import mk.ukim.finki.gitcontributionanalyzer.exception.AiProviderException;
import mk.ukim.finki.gitcontributionanalyzer.model.AiProviderSelection;
import mk.ukim.finki.gitcontributionanalyzer.model.RepositoryData;
import mk.ukim.finki.gitcontributionanalyzer.service.AiAnalysisService;
import mk.ukim.finki.gitcontributionanalyzer.service.AiProviderClient;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;

@Service
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private static final Set<String> BLOCKED_FINISH_REASONS = Set.of(
            "SAFETY",
            "BLOCKLIST",
            "PROHIBITED_CONTENT",
            "SPII"
    );
    private final AiPromptBuilder promptBuilder;
    private final AiProviderClient providerClient;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public AiAnalysisServiceImpl(
            AiPromptBuilder promptBuilder,
            AiProviderClient providerClient,
            ObjectMapper objectMapper,
            Validator validator) {
        this.promptBuilder = promptBuilder;
        this.providerClient = providerClient;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Override
    public ContributionAnalysis analyze(
            String projectDescription,
            RepositoryData repositoryData,
            AiProviderSelection providerSelection,
            String apiKey) {
        AiProvider provider = providerSelection.provider();
        String prompt;
        try {
            prompt = promptBuilder.build(projectDescription, repositoryData);
        } catch (AiProviderException exception) {
            throw new AiProviderException(exception.reason(), provider, exception);
        }

        AiProviderClient.Completion completion = providerClient.complete(
                providerSelection,
                apiKey,
                prompt
        );
        ContributionAnalysis analysis = parseResponse(completion, provider);
        validateResponse(analysis, repositoryData, provider);
        return analysis;
    }

    ContributionAnalysis parseResponse(AiProviderClient.Completion completion) {
        return parseResponse(completion, null);
    }

    private ContributionAnalysis parseResponse(
            AiProviderClient.Completion completion,
            AiProvider provider) {
        if (completion == null) {
            throw new AiProviderException(AiFailureReason.EMPTY_RESPONSE, provider);
        }

        String json = completion.content() == null ? "" : completion.content();
        if (isBlockedResponse(completion.finishReason())) {
            throw new AiProviderException(AiFailureReason.BLOCKED_RESPONSE, provider);
        }
        if (json.isBlank()) {
            throw new AiProviderException(AiFailureReason.EMPTY_RESPONSE, provider);
        }

        try {
            return objectMapper.readValue(extractJson(json), ContributionAnalysis.class);
        } catch (JacksonException exception) {
            throw new AiProviderException(AiFailureReason.INVALID_RESPONSE, provider, exception);
        }
    }

    private boolean isBlockedResponse(String finishReason) {
        if (finishReason == null) {
            return false;
        }
        return BLOCKED_FINISH_REASONS.contains(finishReason.strip().toUpperCase(Locale.ROOT));
    }

    void validateResponse(ContributionAnalysis analysis, RepositoryData repositoryData) {
        validateResponse(analysis, repositoryData, null);
    }

    private void validateResponse(
            ContributionAnalysis analysis,
            RepositoryData repositoryData,
            AiProvider provider) {
        if (analysis == null || !validator.validate(analysis).isEmpty()) {
            throw invalidResponse(provider);
        }

        int percentageSum = 0;
        Set<String> analyzedHashes = new HashSet<>();

        for (ContributorAnalysis contributor : analysis.contributors()) {
            validateContributor(contributor, provider);
            percentageSum += contributor.contributionPercentage();

            for (CommitAnalysis commit : contributor.commitAnalyses()) {
                if (!analyzedHashes.add(commit.hash())) {
                    throw invalidResponse(provider);
                }
            }
        }

        if (percentageSum != 100) {
            throw invalidResponse(provider);
        }

        validateCommitCoverage(repositoryData, analyzedHashes, provider);
    }

    private void validateContributor(ContributorAnalysis contributor, AiProvider provider) {
        ContributionLevel expectedLevel = ContributionLevel.fromPercentage(
                contributor.contributionPercentage()
        );
        if (contributor.contributionLevel() != expectedLevel) {
            throw invalidResponse(provider);
        }
        validateCategorySummary(contributor, provider);
    }

    private void validateCategorySummary(
            ContributorAnalysis contributor,
            AiProvider provider) {
        Map<CommitCategory, Integer> expectedCounts = new EnumMap<>(CommitCategory.class);
        contributor.commitAnalyses().forEach(commit ->
                expectedCounts.merge(commit.category(), 1, Integer::sum));

        Map<CommitCategory, Integer> providedCounts = new EnumMap<>(CommitCategory.class);
        for (CategorySummary summary : contributor.categorySummary()) {
            if (providedCounts.put(summary.category(), summary.commitCount()) != null) {
                throw invalidResponse(provider);
            }
        }

        if (!providedCounts.equals(expectedCounts)) {
            throw invalidResponse(provider);
        }
    }

    private void validateCommitCoverage(
            RepositoryData repositoryData,
            Set<String> analyzedHashes,
            AiProvider provider) {
        Set<String> expectedHashes = new HashSet<>();
        repositoryData.commits().forEach(commit -> expectedHashes.add(commit.hash()));
        if (!analyzedHashes.equals(expectedHashes)) {
            throw invalidResponse(provider);
        }
    }

    private AiProviderException invalidResponse(AiProvider provider) {
        return new AiProviderException(AiFailureReason.INVALID_RESPONSE, provider);
    }

    private String extractJson(String text) {
        String clean = text.strip();
        if (clean.startsWith("```")) {
            clean = clean.replaceFirst("(?is)^```(?:json)?\\s*", "");
            clean = clean.replaceFirst("(?is)\\s*```$", "");
        }
        if (!clean.startsWith("{")) {
            int firstObject = clean.indexOf('{');
            int lastObject = clean.lastIndexOf('}');
            if (firstObject >= 0 && lastObject > firstObject) {
                clean = clean.substring(firstObject, lastObject + 1);
            }
        }
        return clean;
    }
}