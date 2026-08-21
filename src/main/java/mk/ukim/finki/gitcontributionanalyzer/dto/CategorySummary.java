package mk.ukim.finki.gitcontributionanalyzer.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import mk.ukim.finki.gitcontributionanalyzer.enums.CommitCategory;

public record CategorySummary(
        @NotNull CommitCategory category,
        @Positive int commitCount,
        @NotBlank String explanation
) {
}