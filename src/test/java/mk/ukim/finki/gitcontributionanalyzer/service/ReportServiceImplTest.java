package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisRequest;
import mk.ukim.finki.gitcontributionanalyzer.dto.ContributionAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisSource;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStage;
import mk.ukim.finki.gitcontributionanalyzer.enums.EmailDeliveryStatus;
import mk.ukim.finki.gitcontributionanalyzer.enums.GeminiFailureReason;
import mk.ukim.finki.gitcontributionanalyzer.exception.GeminiException;
import mk.ukim.finki.gitcontributionanalyzer.model.ChangedFile;
import mk.ukim.finki.gitcontributionanalyzer.model.EmailDelivery;
import mk.ukim.finki.gitcontributionanalyzer.model.GitCommit;
import mk.ukim.finki.gitcontributionanalyzer.model.RepositoryData;
import mk.ukim.finki.gitcontributionanalyzer.repository.AnalysisReportRepository;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private GitRepositoryService gitRepositoryService;

    @Mock
    private GeminiAnalysisService geminiAnalysisService;

    @Mock
    private LocalAnalysisService localAnalysisService;

    @Mock
    private AnalysisReportRepository reportRepository;

    @Mock
    private AppSettings settings;

    @Mock
    private EmailReportService emailReportService;

    private ReportServiceImpl reportService;
    private RepositoryData repository;
    private AnalysisRequest request;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(
                gitRepositoryService,
                geminiAnalysisService,
                localAnalysisService,
                reportRepository,
                settings,
                emailReportService
        );

        repository = new RepositoryData(
                "https://github.com/team/project",
                "project",
                "main",
                List.of(new GitCommit(
                        "1234567890abcdef",
                        "Ana Developer",
                        "ana@example.com",
                        OffsetDateTime.parse("2026-01-10T10:00:00Z"),
                        "Add planning feature",
                        List.of(new ChangedFile("src/PlanningService.java", 40, 2)),
                        "sample diff"
                ))
        );
        request = new AnalysisRequest();
        request.setRepositoryUrl(repository.url());
        request.setProjectDescription("Team project for planning shared tasks.");
        request.setEmail("mentor@example.com");

        when(gitRepositoryService.readRepository(repository.url())).thenReturn(repository);
        when(emailReportService.sendReport(any()))
                .thenReturn(new EmailDelivery(EmailDeliveryStatus.DISABLED, "Email delivery is disabled."));
    }

    @Test
    void keepsGeminiAsThePrimaryAnalyzer() {
        ContributionAnalysis geminiResult = analysis("Gemini methodology");
        when(settings.geminiModel()).thenReturn("gemini-test-model");
        when(geminiAnalysisService.analyze(any(), any())).thenReturn(geminiResult);
        List<AnalysisStage> stages = new ArrayList<>();
        List<AnalysisSource> sources = new ArrayList<>();

        var report = reportService.createReport(request, progressListener(stages, sources));

        assertThat(report.analysis()).isSameAs(geminiResult);
        assertThat(report.analysisSource()).isEqualTo(AnalysisSource.GEMINI);
        assertThat(report.analysisModel()).isEqualTo("gemini-test-model");
        assertThat(stages).containsExactly(
                AnalysisStage.READING_REPOSITORY,
                AnalysisStage.ANALYZING_WITH_GEMINI,
                AnalysisStage.PREPARING_REPORT,
                AnalysisStage.SAVING_REPORT,
                AnalysisStage.DELIVERING_EMAIL
        );
        assertThat(sources).containsExactly(AnalysisSource.GEMINI);
        verify(localAnalysisService, never()).analyze(any(), any());
        verify(reportRepository).save(report);
    }

    @Test
    void usesLocalAnalysisWhenGeminiIsUnavailable() {
        ContributionAnalysis localResult = analysis("Local analysis methodology");
        when(geminiAnalysisService.analyze(any(), any()))
                .thenThrow(new GeminiException(GeminiFailureReason.RATE_LIMITED));
        when(localAnalysisService.analyze(any(), any())).thenReturn(localResult);
        List<AnalysisStage> stages = new ArrayList<>();
        List<AnalysisSource> sources = new ArrayList<>();

        var report = reportService.createReport(request, progressListener(stages, sources));

        assertThat(report.analysis()).isSameAs(localResult);
        assertThat(report.analysisSource()).isEqualTo(AnalysisSource.LOCAL_FALLBACK);
        assertThat(report.analysisModel()).isEqualTo("Built-in heuristic rules");
        assertThat(report.analysisNotice())
                .contains("request limit or quota")
                .contains("built-in local heuristic analyzer")
                .doesNotContain("API unavailable");
        assertThat(stages).containsExactly(
                AnalysisStage.READING_REPOSITORY,
                AnalysisStage.ANALYZING_WITH_GEMINI,
                AnalysisStage.LOCAL_FALLBACK,
                AnalysisStage.PREPARING_REPORT,
                AnalysisStage.SAVING_REPORT,
                AnalysisStage.DELIVERING_EMAIL
        );
        assertThat(sources).containsExactly(AnalysisSource.LOCAL_FALLBACK);
        verify(localAnalysisService).analyze(request.getProjectDescription(), repository);
        verify(reportRepository).save(report);
    }

    @Test
    void keepsTheOnScreenReportWhenEmailDeliveryFailsUnexpectedly() {
        ContributionAnalysis result = analysis("Gemini methodology");
        when(settings.geminiModel()).thenReturn("gemini-test-model");
        when(geminiAnalysisService.analyze(any(), any())).thenReturn(result);
        when(emailReportService.sendReport(any()))
                .thenThrow(new IllegalStateException("Email template failed"));

        var report = reportService.createReport(request);

        assertThat(report.analysis()).isSameAs(result);
        assertThat(report.emailDelivery().status()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(report.emailDelivery().message()).contains("available on screen");
        verify(reportRepository, times(2)).save(any());
        verify(reportRepository).save(report);
    }

    @Test
    void supportsExistingSynchronousCallersWithoutAProgressListener() {
        ContributionAnalysis result = analysis("Gemini methodology");
        when(settings.geminiModel()).thenReturn("gemini-test-model");
        when(geminiAnalysisService.analyze(any(), any())).thenReturn(result);

        var report = reportService.createReport(request);

        assertThat(report.analysis()).isSameAs(result);
        assertThat(report.emailDelivery().status()).isEqualTo(EmailDeliveryStatus.DISABLED);
    }

    private ContributionAnalysis analysis(String methodology) {
        return new ContributionAnalysis(
                "Project summary",
                "Goal alignment",
                List.of(),
                List.of(),
                "Conclusion",
                methodology
        );
    }

    private AnalysisProgressListener progressListener(
            List<AnalysisStage> stages,
            List<AnalysisSource> sources) {
        return new AnalysisProgressListener() {
            @Override
            public void onStage(AnalysisStage stage) {
                stages.add(stage);
            }

            @Override
            public void onAnalysisSource(AnalysisSource source) {
                sources.add(source);
            }
        };
    }
}