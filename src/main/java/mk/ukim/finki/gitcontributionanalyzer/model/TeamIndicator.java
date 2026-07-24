package mk.ukim.finki.gitcontributionanalyzer.model;

public record TeamIndicator(
        String type,
        String severity,
        String title,
        String explanation
) {
}