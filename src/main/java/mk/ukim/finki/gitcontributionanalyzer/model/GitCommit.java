package mk.ukim.finki.gitcontributionanalyzer.model;
import java.time.OffsetDateTime;
import java.util.List;

public record GitCommit(
        String hash,
        String authorName,
        String authorEmail,
        OffsetDateTime date,
        String message,
        List<ChangedFile> changedFiles,
        String diff
) {
}
