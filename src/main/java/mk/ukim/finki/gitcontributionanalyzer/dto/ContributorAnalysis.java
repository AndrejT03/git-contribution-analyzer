package mk.ukim.finki.gitcontributionanalyzer.dto;
import mk.ukim.finki.gitcontributionanalyzer.enums.ContributionLevel;
import java.util.List;

public record ContributorAnalysis(
        String name,
        String email,
        int contributionPercentage,
        ContributionLevel contributionLevel,
        String summary,
        List<String> mainWork,
        List<CategorySummary> categorySummary,
        List<CommitAnalysis> commitAnalyses,
        List<String> riskFlags
) {
    public int categoryCommitCount() {
        return categorySummary.stream()
                .mapToInt(CategorySummary::commitCount)
                .sum();
    }

    public int featuredCommitCount() {
        int limit = contributionLevel == ContributionLevel.HIGH ? 4 : 3;
        return Math.min(commitAnalyses.size(), limit);
    }

    public List<CommitAnalysis> featuredCommitAnalyses() {
        return commitAnalyses.subList(0, featuredCommitCount());
    }

    public List<CommitAnalysis> remainingCommitAnalyses() {
        return commitAnalyses.subList(featuredCommitCount(), commitAnalyses.size());
    }
}