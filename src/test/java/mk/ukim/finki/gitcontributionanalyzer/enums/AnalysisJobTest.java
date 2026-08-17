package mk.ukim.finki.gitcontributionanalyzer.enums;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisJobStatusDto;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisJob;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class AnalysisJobTest {

    private static final OffsetDateTime STARTED_AT = OffsetDateTime.parse("2026-08-10T12:00:00Z");

    @Test
    void marksGeminiSkippedWhileLocalFallbackIsActive() {
        AnalysisJob job = jobAtGeminiStage()
                .advanceTo(AnalysisStage.LOCAL_FALLBACK, STARTED_AT.plusSeconds(4));

        AnalysisJobStatusDto status = AnalysisJobStatusDto.from(job);

        assertThat(status.progress()).isEqualTo(70);
        assertThat(status.analysisSource()).isEqualTo(AnalysisSource.LOCAL_FALLBACK);
        assertThat(status.stageStates())
                .containsEntry(AnalysisStage.ANALYZING_WITH_GEMINI, AnalysisStageState.SKIPPED)
                .containsEntry(AnalysisStage.LOCAL_FALLBACK, AnalysisStageState.ACTIVE);
        assertThat(status.stageHistory()).containsExactly(
                AnalysisStage.QUEUED,
                AnalysisStage.STARTING,
                AnalysisStage.READING_REPOSITORY,
                AnalysisStage.ANALYZING_WITH_GEMINI,
                AnalysisStage.LOCAL_FALLBACK
        );
    }

    @Test
    void keepsTheFallbackPathAccurateAfterLaterStagesAndCompletion() {
        AnalysisJob job = jobAtGeminiStage()
                .advanceTo(AnalysisStage.LOCAL_FALLBACK, STARTED_AT.plusSeconds(4))
                .advanceTo(AnalysisStage.PREPARING_REPORT, STARTED_AT.plusSeconds(5))
                .complete(UUID.randomUUID(), AnalysisSource.LOCAL_FALLBACK, STARTED_AT.plusSeconds(6));

        AnalysisJobStatusDto status = AnalysisJobStatusDto.from(job);

        assertThat(status.stageStates())
                .containsEntry(AnalysisStage.ANALYZING_WITH_GEMINI, AnalysisStageState.SKIPPED)
                .containsEntry(AnalysisStage.LOCAL_FALLBACK, AnalysisStageState.COMPLETE)
                .containsEntry(AnalysisStage.PREPARING_REPORT, AnalysisStageState.COMPLETE)
                .containsEntry(AnalysisStage.COMPLETED, AnalysisStageState.COMPLETE);
    }

    @Test
    void marksLocalFallbackSkippedWhenGeminiProducesTheReport() {
        AnalysisJob job = jobAtGeminiStage()
                .selectAnalysisSource(AnalysisSource.GEMINI, STARTED_AT.plusSeconds(4))
                .advanceTo(AnalysisStage.PREPARING_REPORT, STARTED_AT.plusSeconds(5));

        AnalysisJobStatusDto status = AnalysisJobStatusDto.from(job);

        assertThat(status.stageStates())
                .containsEntry(AnalysisStage.ANALYZING_WITH_GEMINI, AnalysisStageState.COMPLETE)
                .containsEntry(AnalysisStage.LOCAL_FALLBACK, AnalysisStageState.SKIPPED)
                .containsEntry(AnalysisStage.PREPARING_REPORT, AnalysisStageState.ACTIVE);
    }

    private AnalysisJob jobAtGeminiStage() {
        return AnalysisJob.queued(UUID.randomUUID(), "team/project", STARTED_AT)
                .advanceTo(AnalysisStage.STARTING, STARTED_AT.plusSeconds(1))
                .advanceTo(AnalysisStage.READING_REPOSITORY, STARTED_AT.plusSeconds(2))
                .advanceTo(AnalysisStage.ANALYZING_WITH_GEMINI, STARTED_AT.plusSeconds(3));
    }
}