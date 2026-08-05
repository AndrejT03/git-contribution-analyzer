package mk.ukim.finki.gitcontributionanalyzer.model;
import mk.ukim.finki.gitcontributionanalyzer.dto.ContributionAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisSource;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AnalysisReport(
        UUID id,
        String repositoryUrl,
        String repositoryName,
        String defaultBranch,
        String projectDescription,
        String requestedEmail,
        AnalysisSource analysisSource,
        String analysisModel,
        String analysisNotice,
        int analyzedCommitCount,
        OffsetDateTime generatedAt,
        ContributionAnalysis analysis,
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
                analysisSource,
                analysisModel,
                analysisNotice,
                analyzedCommitCount,
                generatedAt,
                analysis,
                delivery
        );
    }
}