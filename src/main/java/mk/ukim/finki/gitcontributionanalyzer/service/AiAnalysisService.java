package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.dto.ContributionAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.model.AiProviderSelection;
import mk.ukim.finki.gitcontributionanalyzer.model.RepositoryData;

public interface AiAnalysisService {
    ContributionAnalysis analyze(
            String projectDescription,
            RepositoryData repositoryData,
            AiProviderSelection providerSelection,
            String apiKey);
}