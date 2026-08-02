package mk.ukim.finki.gitcontributionanalyzer.dto;

public record CommitAnalysis(
        String hash,
        String message,
        String category,
        int importance,
        String explanation
) {
}