package mk.ukim.finki.gitcontributionanalyzer.dto;
import mk.ukim.finki.gitcontributionanalyzer.enums.TeamIndicatorSeverity;

public record TeamIndicator(
        String type,
        TeamIndicatorSeverity severity,
        String title,
        String explanation
) {
}