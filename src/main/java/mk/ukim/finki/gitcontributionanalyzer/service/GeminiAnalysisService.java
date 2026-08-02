package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.dto.GeminiAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.model.RepositoryData;

public interface GeminiAnalysisService {
    GeminiAnalysis analyze(String projectDescription, RepositoryData repositoryData);
}