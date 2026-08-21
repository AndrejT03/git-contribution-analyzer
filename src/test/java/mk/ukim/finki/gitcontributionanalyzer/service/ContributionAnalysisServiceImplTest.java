package mk.ukim.finki.gitcontributionanalyzer.service;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.dto.ContributionAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.dto.ContributorAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.enums.*;
import mk.ukim.finki.gitcontributionanalyzer.exception.GeminiException;
import mk.ukim.finki.gitcontributionanalyzer.model.GitCommit;
import mk.ukim.finki.gitcontributionanalyzer.model.RepositoryData;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.GeminiAnalysisServiceImpl;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.GeminiPromptBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.net.http.HttpTimeoutException;
import java.time.OffsetDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContributionAnalysisServiceImplTest {

    private static ValidatorFactory validatorFactory;

    private ObjectMapper objectMapper;
    private GeminiAnalysisServiceImpl service;

    @BeforeAll
    static void createValidatorFactory() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        AppSettings settings = new AppSettings(
                80,
                6000,
                120,
                "test-api-key",
                "gemini-test-model",
                60,
                false,
                ""
        );
        objectMapper = new ObjectMapper();
        service = new GeminiAnalysisServiceImpl(
                settings,
                mock(GeminiPromptBuilder.class),
                objectMapper,
                validatorFactory.getValidator()
        );
    }

    @Test
    void parsesStructuredGeminiResponse() {
        String analysisJson = """
                {
                  "projectSummary": "Team web project.",
                  "goalAlignment": "The changes align with the project goal.",
                  "contributors": [
                    {
                      "name": "Boris",
                      "email": "boris@example.com",
                      "contributionPercentage": 20,
                      "contributionLevel": "MEDIUM",
                      "summary": "He documented the flow.",
                      "mainWork": ["Documentation"],
                      "categorySummary": [{"category":"DOCUMENTATION","commitCount":1,"explanation":"Project guide"}],
                      "commitAnalyses": [{"hash":"abcdef1234567890","message":"Document login","category":"DOCUMENTATION","importance":2,"explanation":"Supporting change"}],
                      "riskFlags": []
                    },
                    {
                      "name": "Ana",
                      "email": "ana@example.com",
                      "contributionPercentage": 80,
                      "contributionLevel": "HIGH",
                      "summary": "She implemented the core functionality.",
                      "mainWork": ["User login"],
                      "categorySummary": [{"category":"FUNCTIONAL","commitCount":1,"explanation":"Core functionality"}],
                      "commitAnalyses": [{"hash":"1234567890abcdef","message":"Add login","category":"FUNCTIONAL","importance":5,"explanation":"Key change"}],
                      "riskFlags": []
                    }
                  ],
                  "teamIndicators": [{
                    "type": "BALANCE",
                    "severity": "INFO",
                    "title": "Balanced contribution",
                    "explanation": "No strong imbalance was found."
                  }],
                  "conclusion": "The contribution is clearly visible.",
                  "methodology": "Gemini compared the diffs with the project goal."
                }
                """;

        var response = objectMapper.createObjectNode();
        response.putArray("candidates")
                .addObject()
                .putObject("content")
                .putArray("parts")
                .addObject()
                .put("text", analysisJson);

        ContributionAnalysis analysis = service.parseResponse(response);

        assertThat(analysis.projectSummary()).isEqualTo("Team web project.");
        assertThat(analysis.contributors())
                .extracting(ContributorAnalysis::name, ContributorAnalysis::contributionPercentage)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Ana", 80),
                        org.assertj.core.groups.Tuple.tuple("Boris", 20)
                );
        assertThat(analysis.contributors().getFirst().contributionLevel()).isEqualTo(ContributionLevel.HIGH);
        assertThat(analysis.contributors().getFirst().commitAnalyses().getFirst().category())
                .isEqualTo(CommitCategory.FUNCTIONAL);
        assertThat(analysis.teamIndicators().getFirst().severity()).isEqualTo(TeamIndicatorSeverity.INFO);
    }

    @Test
    void rejectsUnknownEnumValueAsAControlledGeminiError() {
        String analysisJson = """
                {
                  "projectSummary": "Team web project.",
                  "goalAlignment": "The changes align with the project goal.",
                  "contributors": [{
                    "name": "Ana",
                    "email": "ana@example.com",
                    "contributionPercentage": 100,
                    "contributionLevel": "UNSUPPORTED",
                    "summary": "Core functionality.",
                    "mainWork": [],
                    "categorySummary": [],
                    "commitAnalyses": [],
                    "riskFlags": []
                  }],
                  "teamIndicators": [],
                  "conclusion": "Conclusion.",
                  "methodology": "Methodology."
                }
                """;

        var response = objectMapper.createObjectNode();
        response.putArray("candidates")
                .addObject()
                .putObject("content")
                .putArray("parts")
                .addObject()
                .put("text", analysisJson);

        assertThatThrownBy(() -> service.parseResponse(response))
                .isInstanceOf(GeminiException.class)
                .satisfies(exception -> assertThat(((GeminiException) exception).reason())
                        .isEqualTo(GeminiFailureReason.INVALID_RESPONSE));
    }

    @Test
    void rejectsContributionLevelThatDoesNotMatchPercentage() {
        ContributorAnalysis contributor = new ContributorAnalysis(
                "Ana",
                "ana@example.com",
                100,
                ContributionLevel.LOW,
                "Core contribution.",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        ContributionAnalysis analysis = new ContributionAnalysis(
                "Project summary",
                "Goal alignment",
                List.of(contributor),
                List.of(),
                "Conclusion",
                "Methodology"
        );
        RepositoryData repository = new RepositoryData(
                "https://github.com/team/project",
                "project",
                "main",
                List.of()
        );

        assertThatThrownBy(() -> service.validateResponse(analysis, repository))
                .isInstanceOf(GeminiException.class)
                .satisfies(exception -> assertThat(((GeminiException) exception).reason())
                        .isEqualTo(GeminiFailureReason.INVALID_RESPONSE));
    }

    @Test
    void rejectsNullContributorAsAControlledGeminiError() {
        String analysisJson = """
                {
                  "projectSummary": "Project summary",
                  "goalAlignment": "Goal alignment",
                  "contributors": [null],
                  "teamIndicators": [],
                  "conclusion": "Conclusion",
                  "methodology": "Gemini methodology"
                }
                """;

        var response = objectMapper.createObjectNode();
        response.putArray("candidates")
                .addObject()
                .putObject("content")
                .putArray("parts")
                .addObject()
                .put("text", analysisJson);

        ContributionAnalysis analysis = service.parseResponse(response);
        RepositoryData repository = new RepositoryData(
                "https://github.com/team/project",
                "project",
                "main",
                List.of()
        );

        assertThatThrownBy(() -> service.validateResponse(analysis, repository))
                .isInstanceOf(GeminiException.class)
                .satisfies(exception -> assertThat(((GeminiException) exception).reason())
                        .isEqualTo(GeminiFailureReason.INVALID_RESPONSE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "blank-project-summary",
            "negative-contribution-percentage",
            "null-contribution-level",
            "blank-main-work",
            "null-risk-flag",
            "null-category-summary-entry",
            "null-summary-category",
            "unknown-summary-category",
            "mismatched-summary-count",
            "duplicate-summary-category",
            "null-commit-category",
            "unknown-commit-category",
            "short-commit-hash",
            "invalid-commit-importance",
            "null-team-indicator-entry",
            "null-indicator-severity",
            "unknown-indicator-severity"
    })
    void rejectsMalformedNestedFieldsAsAControlledGeminiError(String invalidField) throws Exception {
        ObjectNode analysisJson = (ObjectNode) objectMapper.readTree(validNestedAnalysisJson());
        ObjectNode contributor = (ObjectNode) analysisJson.path("contributors").path(0);
        ObjectNode categorySummary = (ObjectNode) contributor.path("categorySummary").path(0);
        ObjectNode commitAnalysis = (ObjectNode) contributor.path("commitAnalyses").path(0);
        ObjectNode teamIndicator = (ObjectNode) analysisJson.path("teamIndicators").path(0);

        switch (invalidField) {
            case "blank-project-summary" -> analysisJson.put("projectSummary", " ");
            case "negative-contribution-percentage" -> contributor.put("contributionPercentage", -1);
            case "null-contribution-level" -> contributor.putNull("contributionLevel");
            case "blank-main-work" -> replaceWithInvalidText(contributor, "mainWork", "");
            case "null-risk-flag" -> replaceWithInvalidText(contributor, "riskFlags", null);
            case "null-category-summary-entry" -> {
                ArrayNode summaries = (ArrayNode) contributor.path("categorySummary");
                summaries.removeAll();
                summaries.addNull();
            }
            case "null-summary-category" -> categorySummary.putNull("category");
            case "unknown-summary-category" -> categorySummary.put("category", "UNKNOWN");
            case "mismatched-summary-count" -> categorySummary.put("commitCount", 2);
            case "duplicate-summary-category" ->
                    ((ArrayNode) contributor.path("categorySummary")).add(categorySummary.deepCopy());
            case "null-commit-category" -> commitAnalysis.putNull("category");
            case "unknown-commit-category" -> commitAnalysis.put("category", "UNKNOWN");
            case "short-commit-hash" -> commitAnalysis.put("hash", "abc123");
            case "invalid-commit-importance" -> commitAnalysis.put("importance", 6);
            case "null-team-indicator-entry" -> {
                ArrayNode indicators = (ArrayNode) analysisJson.path("teamIndicators");
                indicators.removeAll();
                indicators.addNull();
            }
            case "null-indicator-severity" -> teamIndicator.putNull("severity");
            case "unknown-indicator-severity" -> teamIndicator.put("severity", "LOUD");
            default -> throw new IllegalArgumentException("Unknown test field: " + invalidField);
        }

        assertThatThrownBy(() -> {
            ContributionAnalysis analysis = parseAnalysis(analysisJson.toString());
            service.validateResponse(analysis, repositoryWithSingleCommit());
        })
                .isInstanceOf(GeminiException.class)
                .satisfies(exception -> assertThat(((GeminiException) exception).reason())
                        .isEqualTo(GeminiFailureReason.INVALID_RESPONSE));
    }

    @Test
    void identifiesBlockedCandidateWithoutExposingProviderDetails() {
        var response = objectMapper.createObjectNode();
        response.putArray("candidates")
                .addObject()
                .put("finishReason", "SAFETY");

        assertThatThrownBy(() -> service.parseResponse(response))
                .isInstanceOf(GeminiException.class)
                .satisfies(exception -> {
                    GeminiException geminiException = (GeminiException) exception;
                    assertThat(geminiException.reason()).isEqualTo(GeminiFailureReason.BLOCKED_RESPONSE);
                    assertThat(geminiException.getMessage()).doesNotContain("SAFETY");
                });
    }

    @Test
    void mapsProviderStatusesToSafeFailureReasons() {
        assertThat(mapStatus(HttpStatus.UNAUTHORIZED)).isEqualTo(GeminiFailureReason.CREDENTIALS_REJECTED);
        assertThat(mapStatus(HttpStatus.FORBIDDEN)).isEqualTo(GeminiFailureReason.CREDENTIALS_REJECTED);
        assertThat(mapStatus(HttpStatus.NOT_FOUND)).isEqualTo(GeminiFailureReason.MODEL_UNAVAILABLE);
        assertThat(mapStatus(HttpStatus.TOO_MANY_REQUESTS)).isEqualTo(GeminiFailureReason.RATE_LIMITED);
        assertThat(mapStatus(HttpStatus.SERVICE_UNAVAILABLE)).isEqualTo(GeminiFailureReason.SERVICE_UNAVAILABLE);
    }

    @Test
    void distinguishesTimeoutFromOtherNetworkFailures() {
        GeminiException timeout = service.mapRestClientFailure(new ResourceAccessException(
                "provider details",
                new HttpTimeoutException("timed out")
        ));
        GeminiException network = service.mapRestClientFailure(new ResourceAccessException("provider details"));

        assertThat(timeout.reason()).isEqualTo(GeminiFailureReason.TIMEOUT);
        assertThat(timeout.category()).isEqualTo(GeminiFailureCategory.CONNECTIVITY);
        assertThat(network.reason()).isEqualTo(GeminiFailureReason.NETWORK_ERROR);
        assertThat(timeout.userMessage()).doesNotContain("provider details");
    }

    private GeminiFailureReason mapStatus(HttpStatus status) {
        RestClientResponseException exception = mock(RestClientResponseException.class);
        when(exception.getStatusCode()).thenReturn(status);
        return service.mapRestClientFailure(exception).reason();
    }

    private ContributionAnalysis parseAnalysis(String analysisJson) {
        var response = objectMapper.createObjectNode();
        response.putArray("candidates")
                .addObject()
                .putObject("content")
                .putArray("parts")
                .addObject()
                .put("text", analysisJson);
        return service.parseResponse(response);
    }

    private void replaceWithInvalidText(ObjectNode parent, String field, String value) {
        ArrayNode values = (ArrayNode) parent.path(field);
        values.removeAll();
        if (value == null) {
            values.addNull();
        } else {
            values.add(value);
        }
    }

    private RepositoryData repositoryWithSingleCommit() {
        return new RepositoryData(
                "https://github.com/team/project",
                "project",
                "main",
                List.of(new GitCommit(
                        "abcdef1234567890",
                        "Ana",
                        "ana@example.com",
                        OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                        "Add feature",
                        List.of(),
                        ""
                ))
        );
    }

    private String validNestedAnalysisJson() {
        return """
                {
                  "projectSummary": "Project summary",
                  "goalAlignment": "Goal alignment",
                  "contributors": [{
                    "name": "Ana",
                    "email": "ana@example.com",
                    "contributionPercentage": 100,
                    "contributionLevel": "HIGH",
                    "summary": "Implemented the feature.",
                    "mainWork": ["Feature implementation"],
                    "categorySummary": [{
                      "category": "FUNCTIONAL",
                      "commitCount": 1,
                      "explanation": "Feature work."
                    }],
                    "commitAnalyses": [{
                      "hash": "abcdef1234567890",
                      "message": "Add feature",
                      "category": "FUNCTIONAL",
                      "importance": 5,
                      "explanation": "Implements the goal."
                    }],
                    "riskFlags": ["Review shared ownership."]
                  }],
                  "teamIndicators": [{
                    "type": "BALANCE",
                    "severity": "INFO",
                    "title": "Balanced work",
                    "explanation": "No critical imbalance was detected."
                  }],
                  "conclusion": "Conclusion",
                  "methodology": "Gemini methodology"
                }
                """;
    }
}