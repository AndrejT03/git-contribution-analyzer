package mk.ukim.finki.gitcontributionanalyzer.model;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisJobStatus;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisSource;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStage;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AnalysisJobTest {

    @Test
    void definesBoundedMonotonicProgressStagesEndingAtOneHundred() {
        assertThat(AnalysisStage.values())
                .extracting(AnalysisStage::progress)
                .allMatch(progress -> progress >= 0 && progress <= 100)
                .isSortedAccordingTo(Comparator.naturalOrder());
        assertThat(AnalysisStage.COMPLETED.progress()).isEqualTo(100);
    }

    @Test
    void keepsProgressMonotonicWhenAnOlderStageArrivesLate() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-16T12:00:00Z");
        AnalysisJob job = AnalysisJob.queued(UUID.randomUUID(), now)
                .advanceTo(AnalysisStage.ANALYZING_WITH_GEMINI, now.plusSeconds(1));

        AnalysisJob unchanged = job.advanceTo(
                AnalysisStage.READING_REPOSITORY,
                now.plusSeconds(2)
        );

        assertThat(unchanged).isSameAs(job);
        assertThat(unchanged.progress()).isEqualTo(55);
    }

    @Test
    void terminalJobsCannotBeChangedByLateWorkerUpdates() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-16T12:00:00Z");
        UUID reportId = UUID.randomUUID();
        AnalysisJob completed = AnalysisJob.queued(UUID.randomUUID(), now)
                .complete(reportId, now.plusSeconds(1));

        AnalysisJob unchanged = completed.fail("late failure", now.plusSeconds(2));

        assertThat(unchanged).isSameAs(completed);
        assertThat(unchanged.reportId()).isEqualTo(reportId);
        assertThat(unchanged.status()).isEqualTo(AnalysisJobStatus.COMPLETED);
    }

    @Test
    void completedStageCanOnlyBeReachedWithAStoredReportId() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-16T12:00:00Z");
        AnalysisJob queued = AnalysisJob.queued(UUID.randomUUID(), now);

        AnalysisJob unchanged = queued.advanceTo(AnalysisStage.COMPLETED, now.plusSeconds(1));

        assertThat(unchanged).isSameAs(queued);
        assertThat(unchanged.status()).isEqualTo(AnalysisJobStatus.QUEUED);
        assertThat(unchanged.progress()).isZero();
        assertThat(unchanged.reportId()).isNull();
    }

    @Test
    void preservesTheRealStageHistoryAndSelectedAnalyzerPath() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-16T12:00:00Z");
        AnalysisJob fallback = AnalysisJob.queued(UUID.randomUUID(), now)
                .advanceTo(AnalysisStage.STARTING, now.plusSeconds(1))
                .advanceTo(AnalysisStage.READING_REPOSITORY, now.plusSeconds(2))
                .advanceTo(AnalysisStage.ANALYZING_WITH_GEMINI, now.plusSeconds(3))
                .advanceTo(AnalysisStage.LOCAL_FALLBACK, now.plusSeconds(4));

        AnalysisJob duplicateUpdate = fallback.advanceTo(
                AnalysisStage.LOCAL_FALLBACK,
                now.plusSeconds(6)
        );

        assertThat(duplicateUpdate).isSameAs(fallback);
        assertThat(fallback.analysisSource()).isEqualTo(AnalysisSource.LOCAL_FALLBACK);
        assertThat(fallback.stageHistory()).containsExactly(
                AnalysisStage.QUEUED,
                AnalysisStage.STARTING,
                AnalysisStage.READING_REPOSITORY,
                AnalysisStage.ANALYZING_WITH_GEMINI,
                AnalysisStage.LOCAL_FALLBACK
        );
        assertThatThrownBy(() -> fallback.stageHistory().add(AnalysisStage.PREPARING_REPORT))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}