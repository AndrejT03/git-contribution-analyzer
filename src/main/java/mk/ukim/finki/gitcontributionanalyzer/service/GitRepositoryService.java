package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.model.RepositoryData;

public interface GitRepositoryService {
    RepositoryData readRepository(String repositoryUrl);
}