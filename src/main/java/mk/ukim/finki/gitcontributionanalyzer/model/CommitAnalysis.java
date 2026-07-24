package mk.ukim.finki.gitcontributionanalyzer.model;

public record CommitAnalysis(
        String hash,
        String message,
        String category,
        int importance,
        String explanation
) {
}