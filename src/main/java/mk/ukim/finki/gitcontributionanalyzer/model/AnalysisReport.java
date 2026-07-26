package mk.ukim.finki.gitcontributionanalyzer.model;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AnalysisReport(
        UUID id,
        String repositoryUrl,
        String repositoryName,
        String defaultBranch,
        String projectDescription,
        String requestedEmail,
        String geminiModel,
        int analyzedCommitCount,
        OffsetDateTime generatedAt,
        GeminiAnalysis analysis,
        EmailDelivery emailDelivery
) {

    public AnalysisReport withEmailDelivery(EmailDelivery delivery) {
        return new AnalysisReport(
                id,
                repositoryUrl,
                repositoryName,
                defaultBranch,
                projectDescription,
                requestedEmail,
                geminiModel,
                analyzedCommitCount,
                generatedAt,
                analysis,
                delivery
        );
    }
}