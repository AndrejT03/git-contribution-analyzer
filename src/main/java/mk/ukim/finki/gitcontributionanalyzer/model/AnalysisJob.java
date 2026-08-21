package mk.ukim.finki.gitcontributionanalyzer.model;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisJobStatus;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisSource;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStage;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AnalysisJob(
        UUID id,
        String repositoryLabel,
        AnalysisJobStatus status,
        AnalysisStage stage,
        AnalysisSource analysisSource,
        List<AnalysisStage> stageHistory,
        UUID reportId,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public AnalysisJob {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(repositoryLabel, "repositoryLabel");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(stageHistory, "stageHistory");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (stageHistory.isEmpty() || !stageHistory.contains(stage)) {
            throw new IllegalArgumentException("Stage history must contain the current stage.");
        }
        stageHistory = List.copyOf(stageHistory);
    }

    public static AnalysisJob queued(UUID id, OffsetDateTime now) {
        return queued(id, "Repository analysis", now);
    }

    public static AnalysisJob queued(UUID id, String repositoryLabel, OffsetDateTime now) {
        return new AnalysisJob(
                id,
                repositoryLabel,
                AnalysisJobStatus.QUEUED,
                AnalysisStage.QUEUED,
                null,
                List.of(AnalysisStage.QUEUED),
                null,
                null,
                now,
                now
        );
    }

    public AnalysisJob advanceTo(AnalysisStage nextStage, OffsetDateTime now) {
        Objects.requireNonNull(nextStage, "nextStage");
        if (isTerminal()
                || nextStage == AnalysisStage.COMPLETED
                || nextStage == stage
                || nextStage.progress() < progress()) {
            return this;
        }

        return new AnalysisJob(
                id,
                repositoryLabel,
                AnalysisJobStatus.RUNNING,
                nextStage,
                nextStage == AnalysisStage.LOCAL_FALLBACK
                        ? AnalysisSource.LOCAL_FALLBACK
                        : analysisSource,
                appendStage(nextStage),
                null,
                null,
                createdAt,
                now
        );
    }

    public AnalysisJob selectAnalysisSource(AnalysisSource selectedSource, OffsetDateTime now) {
        Objects.requireNonNull(selectedSource, "selectedSource");
        if (isTerminal() || analysisSource != null) {
            return this;
        }

        return new AnalysisJob(
                id,
                repositoryLabel,
                status,
                stage,
                selectedSource,
                stageHistory,
                reportId,
                errorMessage,
                createdAt,
                now
        );
    }

    public AnalysisJob complete(UUID completedReportId, OffsetDateTime now) {
        return complete(completedReportId, analysisSource, now);
    }

    public AnalysisJob complete(
            UUID completedReportId,
            AnalysisSource completedAnalysisSource,
            OffsetDateTime now) {
        Objects.requireNonNull(completedReportId, "completedReportId");
        if (isTerminal()) {
            return this;
        }

        return new AnalysisJob(
                id,
                repositoryLabel,
                AnalysisJobStatus.COMPLETED,
                AnalysisStage.COMPLETED,
                completedAnalysisSource,
                appendStage(AnalysisStage.COMPLETED),
                completedReportId,
                null,
                createdAt,
                now
        );
    }

    public AnalysisJob fail(String message, OffsetDateTime now) {
        if (isTerminal()) {
            return this;
        }

        String safeMessage = message == null || message.isBlank()
                ? "The analysis could not be completed. Please try again."
                : message;
        return new AnalysisJob(
                id,
                repositoryLabel,
                AnalysisJobStatus.FAILED,
                stage,
                analysisSource,
                stageHistory,
                null,
                safeMessage,
                createdAt,
                now
        );
    }

    public boolean isTerminal() {
        return status == AnalysisJobStatus.COMPLETED || status == AnalysisJobStatus.FAILED;
    }

    public int progress() {
        return stage.progress();
    }

    private List<AnalysisStage> appendStage(AnalysisStage nextStage) {
        if (stageHistory.contains(nextStage)) {
            return stageHistory;
        }

        List<AnalysisStage> updatedHistory = new ArrayList<>(stageHistory);
        updatedHistory.add(nextStage);
        return List.copyOf(updatedHistory);
    }
}