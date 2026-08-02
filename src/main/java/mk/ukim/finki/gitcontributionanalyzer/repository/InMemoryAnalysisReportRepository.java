package mk.ukim.finki.gitcontributionanalyzer.repository;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import org.springframework.stereotype.Repository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryAnalysisReportRepository implements AnalysisReportRepository {

    private final Map<UUID, AnalysisReport> reports = new ConcurrentHashMap<>();

    @Override
    public void save(AnalysisReport report) { reports.put(report.id(), report); }

    @Override
    public Optional<AnalysisReport> findById(UUID id) { return Optional.ofNullable(reports.get(id)); }
}