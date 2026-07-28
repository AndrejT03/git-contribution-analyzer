package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.exception.GeminiException;
import mk.ukim.finki.gitcontributionanalyzer.model.CommitAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.model.ContributorAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.model.GeminiAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.model.RepositoryData;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GeminiAnalysisService {
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final AppSettings settings;
    private final GeminiPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiAnalysisService(AppSettings settings, GeminiPromptBuilder promptBuilder, ObjectMapper objectMapper) {
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

    public GeminiAnalysis analyze(String projectDescription, RepositoryData repositoryData) {
        requireApiKey();
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

            GeminiAnalysis analysis = parseResponse(response);
            validateResponse(analysis, repositoryData);
            return analysis;
        } catch (RestClientException e) {
            throw new GeminiException("Gemini could not complete the analysis. Check the API key, model, and internet connection.", e);
        }
    }

    private void requireApiKey() {
        String apiKey = settings.geminiApiKey();
        if (apiKey.isBlank() || "your_gemini_api_key".equals(apiKey)) {
            throw new GeminiException("GEMINI_API_KEY is missing from the .env file.");
        }
    }

    GeminiAnalysis parseResponse(JsonNode response) {
        if (response == null) {
            throw new GeminiException("Gemini returned an empty response.");
        }

        String json = response.path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text")
                .asText();

        if (json.isBlank()) {
            String reason = response.path("promptFeedback").path("blockReason").asText();
            throw new GeminiException(reason.isBlank()
                    ? "Gemini did not return an analysis."
                    : "Gemini rejected the request: " + reason);
        }

        try {
            return objectMapper.readValue(removeMarkdownFence(json), GeminiAnalysis.class);
        } catch (JacksonException exception) {
            throw new GeminiException("Gemini returned a response that is not a valid JSON report.", exception);
        }
    }

    public void validateResponse(GeminiAnalysis analysis, RepositoryData repositoryData) {
        if(analysis == null || analysis.contributors() == null || analysis.contributors().isEmpty()) {
            throw new GeminiException("The Gemini response contains no contributor analyses.");
        }
        if (isBlank(analysis.projectSummary())
                || isBlank(analysis.goalAlignment())
                || isBlank(analysis.conclusion())
                || isBlank(analysis.methodology())
                || analysis.teamIndicators() == null) {
            throw new GeminiException("Gemini returned an incomplete report.");
        }

        int persentageSum = analysis.contributors().stream()
                .mapToInt(ContributorAnalysis::contributionPercentage)
                .sum();
        if (persentageSum != 100) {
            throw new GeminiException("Gemini percentages do not add up to 100%. Try again.");
        }

        Set<String> expectedHashes = new HashSet<>();
        repositoryData.commits().forEach(commit-> expectedHashes.add(commit.hash()));

        Set<String> analyzedHashes = new HashSet<>();
        int analyzedCommitCount = 0;

        for (ContributorAnalysis contributor: analysis.contributors()) {
            if (isBlank(contributor.name())
                    || isBlank(contributor.email())
                    || isBlank(contributor.summary())
                    || contributor.mainWork() == null
                    || contributor.categorySummary() == null
                    || contributor.riskFlags() == null) {
                throw new GeminiException("Gemini returned incomplete contributor data.");
            }

            if(contributor.contributionPercentage() < 0 || contributor.contributionPercentage() > 100) {
                throw new GeminiException("Gemini returned an invalid contribution percentage.");
            }
            if(contributor.commitAnalyses() == null) {
                throw new GeminiException("Gemini did not return commit analyses for every contributor.");
            }
            for (CommitAnalysis commit : contributor.commitAnalyses()) {
                analyzedCommitCount++;
                if (isBlank(commit.hash())
                        || commit.hash().length() < 7
                        || isBlank(commit.message())
                        || isBlank(commit.category())
                        || isBlank(commit.explanation())
                        || commit.importance() < 1
                        || commit.importance() > 5) {
                    throw new GeminiException("Gemini returned an incomplete commit analysis.");
                }
                analyzedHashes.add(commit.hash());
            }
        }

        if(!analyzedHashes.equals(expectedHashes) || analyzedCommitCount != expectedHashes.size()) {
            throw new GeminiException("Gemini did not analyze every commit exactly once. Try again.");
        }
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