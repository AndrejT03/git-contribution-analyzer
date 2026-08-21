package mk.ukim.finki.gitcontributionanalyzer.dto;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisJobStatus;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisSource;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStage;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStageState;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisJob;
import java.util.*;

public record AnalysisJobStatusDto(
        UUID id,
        String repositoryLabel,
        AnalysisJobStatus status,
        AnalysisStage stage,
        String stageLabel,
        int progress,
        AnalysisSource analysisSource,
        List<AnalysisStage> stageHistory,
        Map<AnalysisStage, AnalysisStageState> stageStates,
        String message,
        String reportUrl
) {
    public static AnalysisJobStatusDto from(AnalysisJob job) {
        String message = job.errorMessage() == null
                ? job.stage().message()
                : job.errorMessage();
        String reportUrl = job.reportId() == null
                ? null
                : "/reports/" + job.reportId() + "?newReport=true";

        return new AnalysisJobStatusDto(
                job.id(),
                job.repositoryLabel(),
                job.status(),
                job.stage(),
                job.stage().label(),
                job.progress(),
                job.analysisSource(),
                job.stageHistory(),
                stageStates(job),
                message,
                reportUrl
        );
    }

    private static Map<AnalysisStage, AnalysisStageState> stageStates(AnalysisJob job) {
        EnumMap<AnalysisStage, AnalysisStageState> states = new EnumMap<>(AnalysisStage.class);
        AnalysisStage skippedStage = switch (job.analysisSource()) {
            case GEMINI -> AnalysisStage.LOCAL_FALLBACK;
            case LOCAL_FALLBACK -> AnalysisStage.ANALYZING_WITH_GEMINI;
            case null -> null;
        };

        for (AnalysisStage candidate : AnalysisStage.values()) {
            AnalysisStageState state;
            if (candidate == skippedStage) {
                state = AnalysisStageState.SKIPPED;
            } else if (candidate == job.stage()) {
                state = job.status() == AnalysisJobStatus.COMPLETED
                        ? AnalysisStageState.COMPLETE
                        : AnalysisStageState.ACTIVE;
            } else if (job.stageHistory().contains(candidate)) {
                state = AnalysisStageState.COMPLETE;
            } else {
                state = AnalysisStageState.PENDING;
            }
            states.put(candidate, state);
        }
        return Collections.unmodifiableMap(states);
    }
}