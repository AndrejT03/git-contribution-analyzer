package mk.ukim.finki.gitcontributionanalyzer.service.impl;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.dto.*;
import mk.ukim.finki.gitcontributionanalyzer.enums.ContributionLevel;
import mk.ukim.finki.gitcontributionanalyzer.enums.GeminiFailureReason;
import mk.ukim.finki.gitcontributionanalyzer.exception.GeminiException;
import mk.ukim.finki.gitcontributionanalyzer.model.RepositoryData;
import mk.ukim.finki.gitcontributionanalyzer.service.GeminiAnalysisService;
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
public class GeminiAnalysisServiceImpl implements GeminiAnalysisService {

    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final AppSettings settings;
    private final GeminiPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiAnalysisServiceImpl(
            AppSettings settings,
            GeminiPromptBuilder promptBuilder,
            ObjectMapper objectMapper) {

        this.settings = settings;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(settings.geminiTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .baseUrl(GEMINI_BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public ContributionAnalysis analyze(String projectDescription, RepositoryData repositoryData) {
        requireConfiguration();
        String Prompt = promptBuilder.build(projectDescription, repositoryData);

        Map<String, Object> request = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", Prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json",
                        "maxOutputTokens", 32768
                )
        );

        try {
            JsonNode response = restClient.post()
                    .uri("/models/{model}:generateContent", settings.geminiModel())
                    .header("x-goog-api-key", settings.geminiApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            ContributionAnalysis analysis = parseResponse(response);
            validateResponse(analysis, repositoryData);
            return analysis;
        } catch (RestClientException e) {
            throw mapRestClientFailure(e);
        }
    }

    public ContributionAnalysis parseResponse(JsonNode response) {
        if (response == null) {
            throw new GeminiException(GeminiFailureReason.EMPTY_RESPONSE);
        }

        String json = response.path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text")
                .asString("");

        if (json.isBlank()) {
            String promptBlockReason = response.path("promptFeedback")
                    .path("blockReason")
                    .asString("");
            String candidateFinishReason = response.path("candidates")
                    .path(0)
                    .path("finishReason")
                    .asString("");
            boolean blocked = !promptBlockReason.isBlank()
                    || "SAFETY".equals(candidateFinishReason)
                    || "BLOCKLIST".equals(candidateFinishReason)
                    || "PROHIBITED_CONTENT".equals(candidateFinishReason)
                    || "SPII".equals(candidateFinishReason);
            throw new GeminiException(blocked
                    ? GeminiFailureReason.BLOCKED_RESPONSE
                    : GeminiFailureReason.EMPTY_RESPONSE);
        }

        try {
            return objectMapper.readValue(removeMarkdownFence(json), ContributionAnalysis.class);
        } catch (JacksonException exception) {
            throw new GeminiException(GeminiFailureReason.INVALID_RESPONSE, exception);
        }
    }

    private void requireConfiguration() {
        String apiKey = settings.geminiApiKey();
        if (apiKey.isBlank() || "your_gemini_api_key".equals(apiKey)) {
            throw new GeminiException(GeminiFailureReason.MISSING_API_KEY);
        }
        if (settings.geminiModel().isBlank()) {
            throw new GeminiException(GeminiFailureReason.MISSING_MODEL);
        }
    }

    public void validateResponse(ContributionAnalysis analysis, RepositoryData repositoryData) {
        if (analysis == null || analysis.contributors() == null || analysis.contributors().isEmpty()) {
            throw new GeminiException(GeminiFailureReason.INVALID_RESPONSE);
        }
        if (analysis.contributors().stream().anyMatch(Objects::isNull)) {
            throw new GeminiException(GeminiFailureReason.INVALID_RESPONSE);
        }
        if (isBlank(analysis.projectSummary())
                || isBlank(analysis.goalAlignment())
                || isBlank(analysis.conclusion())
                || isBlank(analysis.methodology())
                || analysis.teamIndicators() == null
                || analysis.teamIndicators().stream().anyMatch(this::isInvalidTeamIndicator)) {
            throw new GeminiException(GeminiFailureReason.INVALID_RESPONSE);
        }

        int percentageSum = analysis.contributors().stream()
                .mapToInt(ContributorAnalysis::contributionPercentage)
                .sum();
        if (percentageSum != 100) {
            throw new GeminiException(GeminiFailureReason.INVALID_RESPONSE);
        }

        Set<String> expectedHashes = new HashSet<>();
        repositoryData.commits().forEach(commit -> expectedHashes.add(commit.hash()));

        Set<String> analyzedHashes = new HashSet<>();
        int analyzedCommitCount = 0;
        for (ContributorAnalysis contributor : analysis.contributors()) {
            if (isBlank(contributor.name())
                    || isBlank(contributor.email())
                    || contributor.contributionLevel() == null
                    || isBlank(contributor.summary())
                    || contributor.mainWork() == null
                    || contributor.categorySummary() == null
                    || contributor.riskFlags() == null
                    || containsBlank(contributor.mainWork())
                    || containsBlank(contributor.riskFlags())
                    || contributor.categorySummary().stream().anyMatch(this::isInvalidCategorySummary)) {
                throw new GeminiException(GeminiFailureReason.INVALID_RESPONSE);
            }
            if (contributor.contributionPercentage() < 0 || contributor.contributionPercentage() > 100) {
                throw new GeminiException(GeminiFailureReason.INVALID_RESPONSE);
            }
            ContributionLevel expectedLevel = ContributionLevel.fromPercentage(
                    contributor.contributionPercentage()
            );
            if (contributor.contributionLevel() != expectedLevel) {
                throw new GeminiException(GeminiFailureReason.INVALID_RESPONSE);
            }
            if (contributor.commitAnalyses() == null) {
                throw new GeminiException(GeminiFailureReason.INVALID_RESPONSE);
            }
            for (CommitAnalysis commit : contributor.commitAnalyses()) {
                if (commit == null) {
                    throw new GeminiException(GeminiFailureReason.INVALID_RESPONSE);
                }
                analyzedCommitCount++;
                if (isBlank(commit.hash())
                        || commit.hash().length() < 7
                        || isBlank(commit.message())
                        || commit.category() == null
                        || isBlank(commit.explanation())
                        || commit.importance() < 1
                        || commit.importance() > 5) {
                    throw new GeminiException(GeminiFailureReason.INVALID_RESPONSE);
                }
                analyzedHashes.add(commit.hash());
            }
        }

        if (!analyzedHashes.equals(expectedHashes) || analyzedCommitCount != expectedHashes.size()) {
            throw new GeminiException(GeminiFailureReason.INVALID_RESPONSE);
        }
    }

    private boolean isInvalidCategorySummary(CategorySummary category) {
        return category == null
                || category.category() == null
                || category.commitCount() < 0
                || isBlank(category.explanation());
    }

    private boolean isInvalidTeamIndicator(TeamIndicator indicator) {
        return indicator == null
                || isBlank(indicator.type())
                || indicator.severity() == null
                || isBlank(indicator.title())
                || isBlank(indicator.explanation());
    }

    private boolean containsBlank(List<String> values) {
        return values.stream().anyMatch(this::isBlank);
    }

    public GeminiException mapRestClientFailure(RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return new GeminiException(mapHttpFailure(status), exception);
        }
        if (exception instanceof ResourceAccessException) {
            GeminiFailureReason reason = hasTimeoutCause(exception)
                    ? GeminiFailureReason.TIMEOUT
                    : GeminiFailureReason.NETWORK_ERROR;
            return new GeminiException(reason, exception);
        }
        return new GeminiException(GeminiFailureReason.SERVICE_UNAVAILABLE, exception);
    }

    private GeminiFailureReason mapHttpFailure(int status) {
        return switch (status) {
            case 400 -> GeminiFailureReason.REQUEST_REJECTED;
            case 401, 403 -> GeminiFailureReason.CREDENTIALS_REJECTED;
            case 404 -> GeminiFailureReason.MODEL_UNAVAILABLE;
            case 429 -> GeminiFailureReason.RATE_LIMITED;
            default -> status >= 500 && status <= 599
                    ? GeminiFailureReason.SERVICE_UNAVAILABLE
                    : GeminiFailureReason.REQUEST_REJECTED;
        };
    }

    private boolean hasTimeoutCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                return false;
            }
            current = cause;
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String removeMarkdownFence(String text) {
        String clean = text.trim();
        if (clean.startsWith("```")) {
            clean = clean.replaceFirst("^```(?:json)?\\s*", "");
            clean = clean.replaceFirst("\\s*```$", "");
        }
        return clean;
    }
}