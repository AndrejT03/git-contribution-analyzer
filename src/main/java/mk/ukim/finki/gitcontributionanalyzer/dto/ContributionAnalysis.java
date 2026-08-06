package mk.ukim.finki.gitcontributionanalyzer.dto;
import java.util.Comparator;
import java.util.List;

public record ContributionAnalysis(
        String projectSummary,
        String goalAlignment,
        List<ContributorAnalysis> contributors,
        List<TeamIndicator> teamIndicators,
        String conclusion,
        String methodology
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