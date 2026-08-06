package mk.ukim.finki.gitcontributionanalyzer.dto;
import mk.ukim.finki.gitcontributionanalyzer.enums.ContributionLevel;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

public class ContributionAnalysisTest {

    @Test
    void ranksContributorsByPercentageThenNameAndEmail() {
        ContributionAnalysis analysis = analysis(List.of(
                contributor("Zoran", "zoran@example.com", 1),
                contributor("Boris", "boris@example.com", 2),
                contributor("ana", "second@example.com", 79),
                contributor("Ana", "first@example.com", 79)
        ));

        assertThat(analysis.contributors())
                .extracting(
                        ContributorAnalysis::contributionPercentage,
                        ContributorAnalysis::name,
                        ContributorAnalysis::email
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(79, "Ana", "first@example.com"),
                        org.assertj.core.groups.Tuple.tuple(79, "ana", "second@example.com"),
                        org.assertj.core.groups.Tuple.tuple(2, "Boris", "boris@example.com"),
                        org.assertj.core.groups.Tuple.tuple(1, "Zoran", "zoran@example.com")
                );
    }

    @Test
    void preservesNullListsSoProviderValidationCanRejectThem() {
        ContributionAnalysis analysis = analysis(null);
        assertThat(analysis.contributors()).isNull();
    }

    private ContributionAnalysis analysis(List<ContributorAnalysis> contributors) {
        return new ContributionAnalysis(
                "Project summary",
                "Goal alignment",
                contributors,
                List.of(),
                "Conclusion",
                "Methodology"
        );
    }

    private ContributorAnalysis contributor(String name, String email, int percentage) {
        return new ContributorAnalysis(
                name,
                email,
                percentage,
                ContributionLevel.fromPercentage(percentage),
                "Summary",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}