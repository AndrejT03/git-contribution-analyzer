package mk.ukim.finki.gitcontributionanalyzer.model;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStage;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStatus;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record AnalysisJob(
        UUID id,
        AnalysisStatus status,
        AnalysisStage stage,
        int progress,
        UUID reportId,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public AnalysisJob {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException("Progress must be between 0 and 100.");
        }
    }

    public static AnalysisJob queued(UUID id, OffsetDateTime now) {
        return new AnalysisJob(
                id,
                AnalysisStatus.QUEUED,
                AnalysisStage.QUEUED,
                AnalysisStage.QUEUED.progress(),
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
                || nextStage.progress() < progress) {
            return this;
        }

        return new AnalysisJob(
                id,
                AnalysisStatus.RUNNING,
                nextStage,
                nextStage.progress(),
                null,
                null,
                createdAt,
                now
        );
    }

    public AnalysisJob complete(UUID completedReportId, OffsetDateTime now) {
        Objects.requireNonNull(completedReportId, "completedReportId");
        if (isTerminal()) {
            return this;
        }

        return new AnalysisJob(
                id,
                AnalysisStatus.COMPLETED,
                AnalysisStage.COMPLETED,
                AnalysisStage.COMPLETED.progress(),
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
                AnalysisStatus.FAILED,
                stage,
                progress,
                null,
                safeMessage,
                createdAt,
                now
        );
    }

    public boolean isTerminal() {
        return status == AnalysisStatus.COMPLETED || status == AnalysisStatus.FAILED;
    }
}