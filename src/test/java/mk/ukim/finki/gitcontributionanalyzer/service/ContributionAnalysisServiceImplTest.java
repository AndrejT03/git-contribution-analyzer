package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.dto.ContributionAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.dto.ContributorAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.enums.*;
import mk.ukim.finki.gitcontributionanalyzer.exception.GeminiException;
import mk.ukim.finki.gitcontributionanalyzer.model.RepositoryData;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.GeminiAnalysisServiceImpl;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.GeminiPromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContributionAnalysisServiceImplTest {

    private ObjectMapper objectMapper;
    private GeminiAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        AppSettings settings = mock(AppSettings.class);
        when(settings.geminiTimeoutSeconds()).thenReturn(60);
        objectMapper = new ObjectMapper();
        service = new GeminiAnalysisServiceImpl(
                settings,
                mock(GeminiPromptBuilder.class),
                objectMapper
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
                .hasMessageContaining("does not match its percentage");
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
}