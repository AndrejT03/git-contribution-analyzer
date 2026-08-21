package mk.ukim.finki.gitcontributionanalyzer.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.List;

public record ContributionAnalysis(
        @NotBlank String projectSummary,
        @NotBlank String goalAlignment,
        @NotEmpty List<@NotNull @Valid ContributorAnalysis> contributors,
        @NotNull List<@NotNull @Valid TeamIndicator> teamIndicators,
        @NotBlank String conclusion,
        @NotBlank String methodology
) {
    private static final Comparator<String> TEXT_ORDER = Comparator.nullsLast(
            String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())
    );

    private static final Comparator<ContributorAnalysis> CONTRIBUTOR_ORDER = Comparator
            .comparingInt(ContributorAnalysis::contributionPercentage)
            .reversed()
            .thenComparing(ContributorAnalysis::name, TEXT_ORDER)
            .thenComparing(ContributorAnalysis::email, TEXT_ORDER);

    public ContributionAnalysis {
        if (contributors != null) {
            contributors = contributors.stream()
                    .sorted(Comparator.nullsLast(CONTRIBUTOR_ORDER))
                    .toList();
        }
    }
}