package mk.ukim.finki.gitcontributionanalyzer.dto;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStage;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStatus;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisJob;

import java.util.UUID;

public record AnalysisJobStatus(
        UUID id,
        AnalysisStatus status,
        AnalysisStage stage,
        String stageLabel,
        int progress,
        String message,
        String reportUrl
) {
    public static AnalysisJobStatus from(AnalysisJob job) {
        String message = job.errorMessage() == null
                ? job.stage().message()
                : job.errorMessage();
        String reportUrl = job.reportId() == null
                ? null
                : "/reports/" + job.reportId() + "?newReport=true";

        return new AnalysisJobStatus(
                job.id(),
                job.status(),
                job.stage(),
                job.stage().label(),
                job.progress(),
                message,
                reportUrl
        );
    }
}
