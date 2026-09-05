package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisRequest;
import mk.ukim.finki.gitcontributionanalyzer.dto.ContributionAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.enums.*;
import mk.ukim.finki.gitcontributionanalyzer.exception.AiProviderException;
import mk.ukim.finki.gitcontributionanalyzer.model.*;
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
    private AiAnalysisService aiAnalysisService;

    @Mock
    private LocalAnalysisService localAnalysisService;

    @Mock
    private AnalysisReportRepository reportRepository;

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
                aiAnalysisService,
                localAnalysisService,
                reportRepository,
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
        request = new AnalysisRequest(
                repository.url(),
                "Team project for planning shared tasks.",
                "mentor@example.com",
                "request-only-secret",
                "openai::gpt-5.6-terra"
        );

        when(gitRepositoryService.readRepository(repository.url())).thenReturn(repository);
        when(emailReportService.sendReport(any()))
                .thenReturn(new EmailDelivery(EmailDeliveryStatus.DISABLED, "Email delivery is disabled."));
    }

    @Test
    void usesTheRequestSelectedProviderAsThePrimaryAnalyzer() {
        ContributionAnalysis providerResult = analysis("AI provider methodology");
        when(aiAnalysisService.analyze(any(), any(), any(), any())).thenReturn(providerResult);
        List<AnalysisStage> stages = new ArrayList<>();
        List<AnalysisSource> sources = new ArrayList<>();

        var report = reportService.createReport(request, progressListener(stages, sources));

        assertThat(report.analysis()).isSameAs(providerResult);
        assertThat(report.analysisSource()).isEqualTo(AnalysisSource.AI_PROVIDER);
        assertThat(report.analysisModel()).isEqualTo("OpenAI · gpt-5.6-terra");
        assertThat(report.analysisNotice()).contains("OpenAI analyzed");
        assertThat(stages).containsExactly(
                AnalysisStage.READING_REPOSITORY,
                AnalysisStage.ANALYZING_WITH_AI,
                AnalysisStage.PREPARING_REPORT,
                AnalysisStage.SAVING_REPORT,
                AnalysisStage.DELIVERING_EMAIL
        );
        assertThat(sources).containsExactly(AnalysisSource.AI_PROVIDER);
        verify(localAnalysisService, never()).analyze(any(), any());
        verify(aiAnalysisService).analyze(
                request.projectDescription(),
                repository,
                AiProviderSelection.parse(request.aiModel()),
                "request-only-secret"
        );
        verify(reportRepository).save(report);
    }

    @Test
    void usesLocalAnalysisWhenTheSelectedProviderIsUnavailable() {
        ContributionAnalysis localResult = analysis("Local analysis methodology");
        when(aiAnalysisService.analyze(any(), any(), any(), any()))
                .thenThrow(new AiProviderException(AiFailureReason.RATE_LIMITED, AiProvider.OPENAI));
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
                AnalysisStage.ANALYZING_WITH_AI,
                AnalysisStage.LOCAL_FALLBACK,
                AnalysisStage.PREPARING_REPORT,
                AnalysisStage.SAVING_REPORT,
                AnalysisStage.DELIVERING_EMAIL
        );
        assertThat(sources).containsExactly(AnalysisSource.LOCAL_FALLBACK);
        verify(localAnalysisService).analyze(request.projectDescription(), repository);
        verify(reportRepository).save(report);
    }

    @Test
    void keepsTheOnScreenReportWhenEmailDeliveryFailsUnexpectedly() {
        ContributionAnalysis result = analysis("AI provider methodology");
        when(aiAnalysisService.analyze(any(), any(), any(), any())).thenReturn(result);
        when(emailReportService.sendReport(any()))
                .thenThrow(new IllegalStateException("Email template failed"));

        var report = reportService.createReport(request, ignored -> { });

        assertThat(report.analysis()).isSameAs(result);
        assertThat(report.emailDelivery().status()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(report.emailDelivery().message()).contains("available on screen");
        verify(reportRepository, times(2)).save(any());
        verify(reportRepository).save(report);
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