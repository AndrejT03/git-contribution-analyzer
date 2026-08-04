package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.dto.CommitAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.model.ChangedFile;
import mk.ukim.finki.gitcontributionanalyzer.model.GitCommit;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.LocalCommitClassifier;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class LocalCommitClassifierTest {

    private final LocalCommitClassifier classifier = new LocalCommitClassifier();

    @Test
    void classifiesDocumentationAndCapsItsImportance() {
        GitCommit commit = commit(
                "Update project guide",
                List.of(new ChangedFile("docs/guide.md", 500, 20))
        );

        CommitAnalysis result = classifier.analyze(commit, "Team planning application");

        assertThat(result.category()).isEqualTo("DOCUMENTATION");
        assertThat(result.importance()).isEqualTo(2);
    }

    @Test
    void detectsBugFixFromCommitMessage() {
        GitCommit commit = commit(
                "Fix login validation bug",
                List.of(new ChangedFile("src/LoginService.java", 18, 4))
        );

        CommitAnalysis result = classifier.analyze(commit, "Secure login application");

        assertThat(result.category()).isEqualTo("BUG_FIX");
        assertThat(result.importance()).isEqualTo(3);
    }

    @Test
    void detectsTestsFromChangedPaths() {
        GitCommit commit = commit(
                "Cover edge cases",
                List.of(new ChangedFile("src/test/LoginServiceTest.java", 40, 0))
        );

        CommitAnalysis result = classifier.analyze(commit, "Secure login application");

        assertThat(result.category()).isEqualTo("TESTING");
        assertThat(result.hash()).isEqualTo("1234567890abcdef");
    }

    private GitCommit commit(String message, List<ChangedFile> files) {
        return new GitCommit(
                "1234567890abcdef",
                "Ana Developer",
                "ana@example.com",
                OffsetDateTime.parse("2026-01-10T10:00:00Z"),
                message,
                files,
                "sample diff"
        );
    }
}