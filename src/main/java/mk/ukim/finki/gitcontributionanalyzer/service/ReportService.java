package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisRequest;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import java.util.UUID;

public interface ReportService {
    AnalysisReport createReport(AnalysisRequest request);
    AnalysisReport createReport(AnalysisRequest request, AnalysisProgressListener progressListener);
    AnalysisReport getReport(UUID id);
}