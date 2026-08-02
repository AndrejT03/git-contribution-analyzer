package mk.ukim.finki.gitcontributionanalyzer.dto;

public record CategorySummary(
        String category,
        int commitCount,
        String explanation
) {
}