package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.exception.ReportNotFoundException;
import mk.ukim.finki.gitcontributionanalyzer.model.*;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ReportService {

    private final GitRepositoryService gitRepositoryService;
    private final GeminiAnalysisService geminiAnalysisService;
    private final InMemoryReportStore reportStore;
    private final AppSettings settings;
    private final EmailReportService emailReportService;

    public ReportService(
            GitRepositoryService gitRepositoryService,
            GeminiAnalysisService geminiAnalysisService,
            InMemoryReportStore reportStore,
            AppSettings settings,
            EmailReportService emailReportService) {
        this.gitRepositoryService = gitRepositoryService;
        this.geminiAnalysisService = geminiAnalysisService;
        this.reportStore = reportStore;
        this.settings = settings;
        this.emailReportService = emailReportService;
    }

    public AnalysisReport createReport(AnalysisRequest request) {
        RepositoryData repository = gitRepositoryService.readRepository(request.getRepositoryUrl());
        GeminiAnalysis analysis = geminiAnalysisService.analyze(request.getProjectDescription(), repository);

        AnalysisReport report = new AnalysisReport(
                UUID.randomUUID(),
                repository.url(),
                repository.name(),
                repository.defaultBranch(),
                request.getProjectDescription(),
                request.getEmail(),
                settings.geminiModel(),
                repository.commits().size(),
                OffsetDateTime.now(),
                analysis,
                EmailDelivery.pending()
        );

        EmailDelivery delivery = emailReportService.sendReport(report);
        report = report.withEmailDelivery(delivery);
        reportStore.save(report);
        return report;
    }

    public AnalysisReport getReport(UUID id) { return reportStore.findById(id).orElseThrow(() ->  new ReportNotFoundException("The report was not found, or the application was restarted.")); }
}