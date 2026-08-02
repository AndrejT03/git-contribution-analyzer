package mk.ukim.finki.gitcontributionanalyzer.dto;
import java.util.List;

public record ContributorAnalysis(
        String name,
        String email,
        int contributionPercentage,
        String contributionLevel,
        String summary,
        List<String> mainWork,
        List<CategorySummary> categorySummary,
        List<CommitAnalysis> commitAnalyses,
        List<String> riskFlags
) {
}