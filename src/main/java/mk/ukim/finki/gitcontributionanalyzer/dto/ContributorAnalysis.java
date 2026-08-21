package mk.ukim.finki.gitcontributionanalyzer.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mk.ukim.finki.gitcontributionanalyzer.enums.ContributionLevel;
import java.util.List;

public record ContributorAnalysis(
        @NotBlank String name,
        @NotBlank String email,
        @Min(0) @Max(100) int contributionPercentage,
        @NotNull ContributionLevel contributionLevel,
        @NotBlank String summary,
        @NotNull List<@NotBlank String> mainWork,
        @NotNull List<@NotNull @Valid CategorySummary> categorySummary,
        @NotNull List<@NotNull @Valid CommitAnalysis> commitAnalyses,
        @NotNull List<@NotBlank String> riskFlags
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