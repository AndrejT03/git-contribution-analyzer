package mk.ukim.finki.gitcontributionanalyzer.model;

public record CategorySummary(
        String category,
        int commitCount,
        String explanation
) {
}