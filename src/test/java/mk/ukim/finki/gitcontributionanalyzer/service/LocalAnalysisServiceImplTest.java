package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.dto.CommitAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.dto.ContributionAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.enums.CommitCategory;
import mk.ukim.finki.gitcontributionanalyzer.model.ChangedFile;
import mk.ukim.finki.gitcontributionanalyzer.model.GitCommit;
import mk.ukim.finki.gitcontributionanalyzer.model.RepositoryData;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.LocalAnalysisServiceImpl;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.LocalCommitClassifier;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAnalysisServiceImplTest {

    private final LocalAnalysisServiceImpl service = new LocalAnalysisServiceImpl(
            new LocalCommitClassifier()
    );

    @Test
    void buildsACompleteAnalysisAndMergesMatchingEmails() {
        RepositoryData repository = new RepositoryData(
                "https://github.com/team/project",
                "project",
                "main",
                List.of(
                        commit("hash-1", "Ana", "ANA@example.com", "Add login feature",
                                new ChangedFile("src/LoginService.java", 90, 10)),
                        commit("hash-2", "Ana Developer", "ana@example.com", "Fix login bug",
                                new ChangedFile("src/LoginService.java", 15, 5)),
                        commit("hash-3", "Boris", "boris@example.com", "Add login tests",
                                new ChangedFile("src/test/LoginServiceTest.java", 45, 0))
                )
        );

        ContributionAnalysis result = service.analyze("Secure team login application", repository);

        assertThat(result.contributors()).hasSize(2);
        assertThat(result.contributors().stream()
                .mapToInt(contributor -> contributor.contributionPercentage())
                .sum()).isEqualTo(100);
        assertThat(result.contributors().getFirst().commitAnalyses()).hasSize(2);
        assertThat(result.contributors().stream()
                .flatMap(contributor -> contributor.commitAnalyses().stream())
                .map(CommitAnalysis::hash))
                .containsExactlyInAnyOrder("hash-1", "hash-2", "hash-3");
        assertThat(result.contributors().stream()
                .flatMap(contributor -> contributor.commitAnalyses().stream())
                .map(CommitAnalysis::category))
                .contains(
                        CommitCategory.FUNCTIONAL,
                        CommitCategory.BUG_FIX,
                        CommitCategory.TESTING
                );
        assertThat(result.methodology()).contains("Local analysis");
    }

    @Test
    void assignsRoundingPointsByLargestFractionalRemainder() {
        RepositoryData repository = new RepositoryData(
                "https://github.com/team/project",
                "project",
                "main",
                List.of(
                        commit("a-1", "Ana", "ana@example.com", "Add alpha",
                                new ChangedFile("src/Alpha.java", 401, 0)),
                        commit("a-2", "Ana", "ana@example.com", "Add beta",
                                new ChangedFile("src/Beta.java", 101, 0)),
                        commit("b-1", "Boris", "boris@example.com", "Add gamma",
                                new ChangedFile("src/Gamma.java", 401, 0)),
                        commit("b-2", "Boris", "boris@example.com", "Add delta",
                                new ChangedFile("src/Delta.java", 11, 0)),
                        commit("c-1", "Chris", "chris@example.com", "Add epsilon",
                                new ChangedFile("src/Epsilon.java", 151, 0))
                )
        );

        ContributionAnalysis result = service.analyze("Team project application", repository);

        assertThat(result.contributors())
                .extracting(contributor -> contributor.contributionPercentage())
                .containsExactly(42, 37, 21);
    }

    @Test
    void ranksLocalContributorsEvenWhenTheSmallestContributorAppearsFirst() {
        RepositoryData repository = new RepositoryData(
                "https://github.com/team/project",
                "project",
                "main",
                List.of(
                        commit("small-1", "Small", "small@example.com", "Format whitespace",
                                new ChangedFile("src/App.java", 1, 0)),
                        commit("large-1", "Large", "large@example.com", "Add planning feature",
                                new ChangedFile("src/PlanningService.java", 500, 20))
                )
        );

        ContributionAnalysis result = service.analyze("Planning feature", repository);

        assertThat(result.contributors())
                .extracting(contributor -> contributor.name())
                .containsExactly("Large", "Small");
        assertThat(result.contributors())
                .extracting(contributor -> contributor.contributionPercentage())
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void rejectsRepositoryWithoutCommits() {
        RepositoryData repository = new RepositoryData(
                "https://github.com/team/project",
                "project",
                "main",
                List.of()
        );

        assertThatThrownBy(() -> service.analyze("Team project application", repository))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one commit");
    }

    private GitCommit commit(
            String hash,
            String name,
            String email,
            String message,
            ChangedFile file) {
        return new GitCommit(
                hash,
                name,
                email,
                OffsetDateTime.parse("2026-01-10T10:00:00Z"),
                message,
                List.of(file),
                "sample diff"
        );
    }
}