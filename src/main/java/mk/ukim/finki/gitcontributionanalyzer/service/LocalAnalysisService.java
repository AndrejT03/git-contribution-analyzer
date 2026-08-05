package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.dto.ContributionAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.model.RepositoryData;

public interface LocalAnalysisService {
    ContributionAnalysis analyze(String projectDescription, RepositoryData repositoryData);
}