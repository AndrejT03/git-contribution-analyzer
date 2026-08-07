package mk.ukim.finki.gitcontributionanalyzer.repository;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisJob;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public interface AnalysisJobRepository {

    void save(AnalysisJob job);

    Optional<AnalysisJob> findById(UUID id);

    Optional<AnalysisJob> update(UUID id, UnaryOperator<AnalysisJob> update);
}