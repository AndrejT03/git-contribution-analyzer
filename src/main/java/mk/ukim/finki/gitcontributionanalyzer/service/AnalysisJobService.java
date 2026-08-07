package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisRequest;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisJob;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisJobService {
    AnalysisJob startAnalysis(AnalysisRequest request);
    Optional<AnalysisJob> findById(UUID id);
}