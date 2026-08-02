package mk.ukim.finki.gitcontributionanalyzer.service.impl;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisRequest;
import mk.ukim.finki.gitcontributionanalyzer.dto.GeminiAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.exception.ReportNotFoundException;
import mk.ukim.finki.gitcontributionanalyzer.model.*;
import mk.ukim.finki.gitcontributionanalyzer.repository.AnalysisReportRepository;
import mk.ukim.finki.gitcontributionanalyzer.service.ReportService;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ReportServiceImpl implements ReportService {

    private final GitRepositoryServiceImpl gitRepositoryServiceImpl;
    private final GeminiAnalysisServiceImpl geminiAnalysisServiceImpl;
    private final AnalysisReportRepository reportRepository;
    private final AppSettings settings;
    private final EmailReportServiceImpl emailReportServiceImpl;

    public ReportServiceImpl(
            GitRepositoryServiceImpl gitRepositoryServiceImpl,
            GeminiAnalysisServiceImpl geminiAnalysisServiceImpl,
            AnalysisReportRepository reportRepository,
            AppSettings settings,
            EmailReportServiceImpl emailReportServiceImpl) {
        this.gitRepositoryServiceImpl = gitRepositoryServiceImpl;
        this.geminiAnalysisServiceImpl = geminiAnalysisServiceImpl;
        this.reportRepository = reportRepository;
        this.settings = settings;
        this.emailReportServiceImpl = emailReportServiceImpl;
    }

    @Override
    public AnalysisReport createReport(AnalysisRequest request) {
        RepositoryData repository = gitRepositoryServiceImpl.readRepository(request.getRepositoryUrl());
        GeminiAnalysis analysis = geminiAnalysisServiceImpl.analyze(request.getProjectDescription(), repository);

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

        EmailDelivery delivery = emailReportServiceImpl.sendReport(report);
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