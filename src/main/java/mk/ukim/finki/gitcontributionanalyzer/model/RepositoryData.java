package mk.ukim.finki.gitcontributionanalyzer.model;
import java.util.List;

public record RepositoryData (
        String url,
        String name,
        String defaultBranch,
        List<GitCommit> commits
) {
}