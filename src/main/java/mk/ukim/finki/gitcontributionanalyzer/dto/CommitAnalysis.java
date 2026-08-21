package mk.ukim.finki.gitcontributionanalyzer.dto;
import jakarta.validation.constraints.*;
import mk.ukim.finki.gitcontributionanalyzer.enums.CommitCategory;

public record CommitAnalysis(
        @NotBlank @Size(min = 7) String hash,
        @NotBlank String message,
        @NotNull CommitCategory category,
        @Min(1) @Max(5) int importance,
        @NotBlank String explanation
) {
}