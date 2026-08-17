package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;

public interface ReportPdfService {
    byte[] createPdf(AnalysisReport report);
}