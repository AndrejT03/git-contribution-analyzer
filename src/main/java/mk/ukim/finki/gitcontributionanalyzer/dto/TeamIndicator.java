package mk.ukim.finki.gitcontributionanalyzer.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mk.ukim.finki.gitcontributionanalyzer.enums.TeamIndicatorSeverity;

public record TeamIndicator(
        @NotBlank String type,
        @NotNull TeamIndicatorSeverity severity,
        @NotBlank String title,
        @NotBlank String explanation
) {
}