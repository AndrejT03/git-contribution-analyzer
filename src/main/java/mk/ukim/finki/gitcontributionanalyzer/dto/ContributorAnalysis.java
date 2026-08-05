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
}