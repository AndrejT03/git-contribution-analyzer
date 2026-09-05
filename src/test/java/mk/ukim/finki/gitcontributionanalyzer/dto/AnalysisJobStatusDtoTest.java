package mk.ukim.finki.gitcontributionanalyzer.dto;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisSource;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStage;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStageState;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisJob;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class AnalysisJobStatusDtoTest {

    private static final OffsetDateTime STARTED_AT = OffsetDateTime.parse("2026-08-10T12:00:00Z");

    @Test
    void marksAiProviderSkippedWhileLocalFallbackIsActive() {
        AnalysisJob job = jobAtAiProviderStage()
                .advanceTo(AnalysisStage.LOCAL_FALLBACK, STARTED_AT.plusSeconds(4));

        AnalysisJobStatusDto status = AnalysisJobStatusDto.from(job);

        assertThat(status.progress()).isEqualTo(70);
        assertThat(status.analysisSource()).isEqualTo(AnalysisSource.LOCAL_FALLBACK);
        assertThat(status.stageStates())
                .containsEntry(AnalysisStage.ANALYZING_WITH_AI, AnalysisStageState.SKIPPED)
                .containsEntry(AnalysisStage.LOCAL_FALLBACK, AnalysisStageState.ACTIVE);
        assertThat(status.stageHistory()).containsExactly(
                AnalysisStage.QUEUED,
                AnalysisStage.STARTING,
                AnalysisStage.READING_REPOSITORY,
                AnalysisStage.ANALYZING_WITH_AI,
                AnalysisStage.LOCAL_FALLBACK
        );
    }

    @Test
    void keepsTheFallbackPathAccurateAfterLaterStagesAndCompletion() {
        AnalysisJob job = jobAtAiProviderStage()
                .advanceTo(AnalysisStage.LOCAL_FALLBACK, STARTED_AT.plusSeconds(4))
                .advanceTo(AnalysisStage.PREPARING_REPORT, STARTED_AT.plusSeconds(5))
                .complete(UUID.randomUUID(), AnalysisSource.LOCAL_FALLBACK, STARTED_AT.plusSeconds(6));

        AnalysisJobStatusDto status = AnalysisJobStatusDto.from(job);

        assertThat(status.stageStates())
                .containsEntry(AnalysisStage.ANALYZING_WITH_AI, AnalysisStageState.SKIPPED)
                .containsEntry(AnalysisStage.LOCAL_FALLBACK, AnalysisStageState.COMPLETE)
                .containsEntry(AnalysisStage.PREPARING_REPORT, AnalysisStageState.COMPLETE)
                .containsEntry(AnalysisStage.COMPLETED, AnalysisStageState.COMPLETE);
    }

    @Test
    void marksLocalFallbackSkippedWhenAiProviderProducesTheReport() {
        AnalysisJob job = jobAtAiProviderStage()
                .selectAnalysisSource(AnalysisSource.AI_PROVIDER, STARTED_AT.plusSeconds(4))
                .advanceTo(AnalysisStage.PREPARING_REPORT, STARTED_AT.plusSeconds(5));

        AnalysisJobStatusDto status = AnalysisJobStatusDto.from(job);

        assertThat(status.stageStates())
                .containsEntry(AnalysisStage.ANALYZING_WITH_AI, AnalysisStageState.COMPLETE)
                .containsEntry(AnalysisStage.LOCAL_FALLBACK, AnalysisStageState.SKIPPED)
                .containsEntry(AnalysisStage.PREPARING_REPORT, AnalysisStageState.ACTIVE);
    }

    private AnalysisJob jobAtAiProviderStage() {
        return AnalysisJob.queued(UUID.randomUUID(), "team/project", STARTED_AT)
                .advanceTo(AnalysisStage.STARTING, STARTED_AT.plusSeconds(1))
                .advanceTo(AnalysisStage.READING_REPOSITORY, STARTED_AT.plusSeconds(2))
                .advanceTo(AnalysisStage.ANALYZING_WITH_AI, STARTED_AT.plusSeconds(3));
    }
}