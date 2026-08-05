package mk.ukim.finki.gitcontributionanalyzer.dto;
import mk.ukim.finki.gitcontributionanalyzer.enums.CommitCategory;

public record CommitAnalysis(
        String hash,
        String message,
        CommitCategory category,
        int importance,
        String explanation
) {
}