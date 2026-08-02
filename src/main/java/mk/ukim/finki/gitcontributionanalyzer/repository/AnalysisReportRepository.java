package mk.ukim.finki.gitcontributionanalyzer.repository;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisReportRepository {

    void save(AnalysisReport report);

    Optional<AnalysisReport> findById(UUID id);
}