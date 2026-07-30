package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.model.GeminiAnalysis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiAnalysisServiceTest {

    private ObjectMapper objectMapper;
    private GeminiAnalysisService service;

    @BeforeEach
    void setUp() {
        AppSettings settings = mock(AppSettings.class);
        when(settings.geminiTimeoutSeconds()).thenReturn(60);
        objectMapper = new ObjectMapper();
        service = new GeminiAnalysisService(
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
                  "contributors": [{
                    "name": "Ana",
                    "email": "ana@example.com",
                    "contributionPercentage": 100,
                    "contributionLevel": "High",
                    "summary": "She implemented the core functionality.",
                    "mainWork": ["User login"],
                    "categorySummary": [{"category":"FUNCTIONAL","commitCount":1,"explanation":"Core functionality"}],
                    "commitAnalyses": [{"hash":"1234567890abcdef","message":"Add login","category":"FUNCTIONAL","importance":5,"explanation":"Key change"}],
                    "riskFlags": []
                  }],
                  "teamIndicators": [],
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

        GeminiAnalysis analysis = service.parseResponse(response);

        assertThat(analysis.projectSummary()).isEqualTo("Team web project.");
        assertThat(analysis.contributors()).hasSize(1);
        assertThat(analysis.contributors().getFirst().contributionPercentage()).isEqualTo(100);
    }
}