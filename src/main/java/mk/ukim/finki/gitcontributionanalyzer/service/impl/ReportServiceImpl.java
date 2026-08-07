package mk.ukim.finki.gitcontributionanalyzer.service.impl;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
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
    public AnalysisReport createReport(AnalysisRequest request) {
        RepositoryData repository = gitRepositoryService.readRepository(request.getRepositoryUrl());
        ContributionAnalysis analysis;
        AnalysisSource analysisSource;
        String analysisModel;
        String analysisNotice;

        try {
            analysis = geminiAnalysisService.analyze(request.getProjectDescription(), repository);
            analysisSource = AnalysisSource.GEMINI;
            analysisModel = settings.geminiModel();
            analysisNotice = "Gemini analyzed the Git history using the supplied project goal.";
        } catch (GeminiException exception) {
            LOGGER.warn(
                    "Gemini analysis failed; using the local fallback. Category: {}, reason: {}",
                    exception.category(),
                    exception.reason()
            );
            analysis = localAnalysisService.analyze(request.getProjectDescription(), repository);
            analysisSource = AnalysisSource.LOCAL_FALLBACK;
            analysisModel = "Built-in heuristic rules";
            analysisNotice = exception.userMessage() + " This report was generated with the built-in local heuristic analyzer.";
        }

        AnalysisReport report = new AnalysisReport(
                UUID.randomUUID(),
                repository.url(),
                repository.name(),
                repository.defaultBranch(),
                request.getProjectDescription(),
                request.getEmail(),
                analysisSource,
                analysisModel,
                analysisNotice,
                repository.commits().size(),
                OffsetDateTime.now(),
                analysis,
                EmailDelivery.pending()
        );

        reportRepository.save(report);

        EmailDelivery delivery;
        try {
            delivery = emailReportService.sendReport(report);
        } catch (RuntimeException exception) {
            LOGGER.warn("The report was generated, but email delivery failed unexpectedly.", exception);
            delivery = new EmailDelivery(
                    EmailDeliveryStatus.FAILED,
                    "The report is available on screen, but email delivery failed unexpectedly."
            );
        }

        report = report.withEmailDelivery(delivery);
        reportRepository.save(report);
        return report;
    }

    @Override
    public AnalysisReport createReport(
            AnalysisRequest request,
            AnalysisProgressListener progressListener) {
        Objects.requireNonNull(progressListener, "progressListener");

        progressListener.onStage(READING_REPOSITORY);
        RepositoryData repository = gitRepositoryService.readRepository(request.getRepositoryUrl());
        ContributionAnalysis analysis;
        AnalysisSource analysisSource;
        String analysisModel;
        String analysisNotice;

        try {
            progressListener.onStage(ANALYZING_WITH_GEMINI);
            analysis = geminiAnalysisService.analyze(request.getProjectDescription(), repository);
            analysisSource = AnalysisSource.GEMINI;
            analysisModel = settings.geminiModel();
            analysisNotice = "Gemini analyzed the Git history using the supplied project goal.";
        } catch (GeminiException exception) {
            LOGGER.warn(
                    "Gemini analysis failed; using the local fallback. Category: {}, reason: {}",
                    exception.category(),
                    exception.reason()
            );
            progressListener.onStage(LOCAL_FALLBACK);
            analysis = localAnalysisService.analyze(request.getProjectDescription(), repository);
            analysisSource = AnalysisSource.LOCAL_FALLBACK;
            analysisModel = "Built-in heuristic rules";
            analysisNotice = exception.userMessage()
                    + " This report was generated with the built-in local heuristic analyzer.";
        }

        progressListener.onStage(PREPARING_REPORT);
        AnalysisReport report = new AnalysisReport(
                UUID.randomUUID(),
                repository.url(),
                repository.name(),
                repository.defaultBranch(),
                request.getProjectDescription(),
                request.getEmail(),
                analysisSource,
                analysisModel,
                analysisNotice,
                repository.commits().size(),
                OffsetDateTime.now(),
                analysis,
                EmailDelivery.pending()
        );

        progressListener.onStage(SAVING_REPORT);
        reportRepository.save(report);

        progressListener.onStage(DELIVERING_EMAIL);
        EmailDelivery delivery;
        try {
            delivery = emailReportService.sendReport(report);
        } catch (RuntimeException exception) {
            LOGGER.warn("The report was generated, but email delivery failed unexpectedly.", exception);
            delivery = new EmailDelivery(
                    EmailDeliveryStatus.FAILED,
                    "The report is available on screen, but email delivery failed unexpectedly."
            );
        }

        report = report.withEmailDelivery(delivery);
        reportRepository.save(report);
        return report;
    }

    @Override
    public AnalysisReport getReport(UUID id) {
        return reportRepository.findById(id)
                .orElseThrow(() ->  new ReportNotFoundException("The report was not found, or the application was restarted."));
    }
}