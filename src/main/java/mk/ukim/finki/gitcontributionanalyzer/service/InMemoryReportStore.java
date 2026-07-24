package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryReportStore {

    private final Map<UUID, AnalysisReport> reports = new ConcurrentHashMap<>();

    public void save(AnalysisReport report) { reports.put(report.id(), report); }

    public Optional<AnalysisReport> findById(UUID id) { return Optional.ofNullable(reports.get(id)); }
}