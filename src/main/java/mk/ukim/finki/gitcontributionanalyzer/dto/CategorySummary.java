package mk.ukim.finki.gitcontributionanalyzer.dto;
import mk.ukim.finki.gitcontributionanalyzer.enums.CommitCategory;

public record CategorySummary(
        CommitCategory category,
        int commitCount,
        String explanation
) {
}