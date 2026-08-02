package mk.ukim.finki.gitcontributionanalyzer.dto;

public record TeamIndicator(
        String type,
        String severity,
        String title,
        String explanation
) {
}