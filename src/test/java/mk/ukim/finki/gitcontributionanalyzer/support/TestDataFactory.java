package mk.ukim.finki.gitcontributionanalyzer.support;
import mk.ukim.finki.gitcontributionanalyzer.dto.*;
import mk.ukim.finki.gitcontributionanalyzer.enums.*;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import mk.ukim.finki.gitcontributionanalyzer.model.EmailDelivery;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class TestDataFactory {

    private TestDataFactory() {
    }

    public static AnalysisReport richAnalysisReport(UUID id) {
        ContributorAnalysis ana = new ContributorAnalysis(
                "Ana Developer",
                "ana@example.com",
                65,
                ContributionLevel.HIGH,
                "She implemented the core functionality.",
                List.of("Login", "User profile"),
                List.of(new CategorySummary(CommitCategory.FUNCTIONAL, 1, "New functionality")),
                List.of(new CommitAnalysis(
                        "1234567890abcdef",
                        "Add login",
                        CommitCategory.FUNCTIONAL,
                        5,
                        "Key project change."
                )),
                List.of("Primary finding", "Secondary finding")
        );
        ContributorAnalysis boris = new ContributorAnalysis(
                "Boris Tester",
                "boris@example.com",
                35,
                ContributionLevel.HIGH,
                "Added tests and fixes.",
                List.of("Integration tests"),
                List.of(new CategorySummary(CommitCategory.TESTING, 1, "Test coverage")),
                List.of(new CommitAnalysis(
                        "abcdef1234567890",
                        "Add tests",
                        CommitCategory.TESTING,
                        4,
                        "Improves reliability."
                )),
                List.of()
        );

        ContributionAnalysis analysis = new ContributionAnalysis(
                "Team collaboration application.",
                "The commits align with the project goal.",
                List.of(ana, boris),
                List.of(new TeamIndicator(
                        "BALANCE",
                        TeamIndicatorSeverity.INFO,
                        "Balanced contribution",
                        "No critical imbalance."
                )),
                "The team achieved the main goal.",
                "Gemini analyzed the commit messages, files, and diffs."
        );

        return new AnalysisReport(
                id,
                "https://github.com/team/project",
                "project",
                "main",
                "Team collaboration and organization application with поддршка for student teams.",
                "mentor@example.com",
                AnalysisSource.GEMINI,
                "gemini-3.7-flash",
                "Gemini analyzed the Git history using the supplied project goal.",
                2,
                OffsetDateTime.parse("2026-08-10T12:00:00+02:00"),
                analysis,
                new EmailDelivery(EmailDeliveryStatus.SENT, "The report was sent.")
        );
    }
}