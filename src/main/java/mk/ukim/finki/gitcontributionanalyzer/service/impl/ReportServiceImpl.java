package mk.ukim.finki.gitcontributionanalyzer.service.impl;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisOutcome;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisRequest;
import mk.ukim.finki.gitcontributionanalyzer.dto.ContributionAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisSource;
import mk.ukim.finki.gitcontributionanalyzer.enums.EmailDeliveryStatus;
import mk.ukim.finki.gitcontributionanalyzer.exception.*;
import mk.ukim.finki.gitcontributionanalyzer.model.*;
import mk.ukim.finki.gitcontributionanalyzer.repository.AnalysisReportRepository;
import mk.ukim.finki.gitcontributionanalyzer.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import static mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStage.*;

@Service
public class ReportServiceImpl implements ReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportServiceImpl.class);

    private final GitRepositoryService gitRepositoryService;
    private final GeminiAnalysisService geminiAnalysisService;
    private final LocalAnalysisService localAnalysisService;
    private final AnalysisReportRepository reportRepository;
    private final AppSettings settings;
    private final EmailReportService emailReportService;

    public ReportServiceImpl(
            GitRepositoryService gitRepositoryService,
            GeminiAnalysisService geminiAnalysisService,
            LocalAnalysisService localAnalysisService,
            AnalysisReportRepository reportRepository,
            AppSettings settings,
            EmailReportService emailReportService) {
        this.gitRepositoryService = gitRepositoryService;
        this.geminiAnalysisService = geminiAnalysisService;
        this.localAnalysisService = localAnalysisService;
        this.reportRepository = reportRepository;
        this.settings = settings;
        this.emailReportService = emailReportService;
    }

    @Override
    public AnalysisReport createReport(
            AnalysisRequest request,
            AnalysisProgressListener progressListener) {
        Objects.requireNonNull(progressListener, "progressListener");

        progressListener.onStage(READING_REPOSITORY);
        RepositoryData repository = gitRepositoryService.readRepository(request.repositoryUrl());
        AnalysisOutcome outcome = analyzeWithFallback(request, repository, progressListener);

        progressListener.onStage(PREPARING_REPORT);
        AnalysisReport report = createPendingReport(request, repository, outcome);

        progressListener.onStage(SAVING_REPORT);
        reportRepository.save(report);

        progressListener.onStage(DELIVERING_EMAIL);
        report = report.withEmailDelivery(deliverEmail(report));
        reportRepository.save(report);
        return report;
    }

    private AnalysisOutcome analyzeWithFallback(
            AnalysisRequest request,
            RepositoryData repository,
            AnalysisProgressListener progressListener) {
        try {
            progressListener.onStage(ANALYZING_WITH_GEMINI);
            ContributionAnalysis analysis = geminiAnalysisService.analyze(
                    request.projectDescription(),
                    repository
            );
            progressListener.onAnalysisSource(AnalysisSource.GEMINI);
            return new AnalysisOutcome(
                    analysis,
                    AnalysisSource.GEMINI,
                    settings.geminiModel(),
                    "Gemini analyzed the Git history using the supplied project goal."
            );
        } catch (GeminiException exception) {
            LOGGER.warn(
                    "Gemini analysis failed; using the local fallback. Category: {}, reason: {}",
                    exception.category(),
                    exception.reason()
            );
            progressListener.onStage(LOCAL_FALLBACK);
            progressListener.onAnalysisSource(AnalysisSource.LOCAL_FALLBACK);
            ContributionAnalysis analysis = localAnalysisService.analyze(
                    request.projectDescription(),
                    repository
            );
            return new AnalysisOutcome(
                    analysis,
                    AnalysisSource.LOCAL_FALLBACK,
                    "Built-in heuristic rules",
                    exception.userMessage()
                            + " This report was generated with the built-in local heuristic analyzer."
            );
        }
    }

    private AnalysisReport createPendingReport(
            AnalysisRequest request,
            RepositoryData repository,
            AnalysisOutcome outcome) {
        return new AnalysisReport(
                UUID.randomUUID(),
                repository.url(),
                repository.name(),
                repository.defaultBranch(),
                request.projectDescription(),
                request.email(),
                outcome.source(),
                outcome.model(),
                outcome.notice(),
                repository.commits().size(),
                OffsetDateTime.now(),
                outcome.analysis(),
                EmailDelivery.pending()
        );
    }

    private EmailDelivery deliverEmail(AnalysisReport report) {
        try {
            return emailReportService.sendReport(report);
        } catch (RuntimeException exception) {
            LOGGER.warn("The report was generated, but email delivery failed unexpectedly.", exception);
            return new EmailDelivery(
                    EmailDeliveryStatus.FAILED,
                    "The report is available on screen, but email delivery failed unexpectedly."
            );
        }
    }

    @Override
    public AnalysisReport getReport(UUID id) {
        return reportRepository.findById(id)
                .orElseThrow(() ->  new ReportNotFoundException("The report was not found, or the application was restarted."));
    }
}