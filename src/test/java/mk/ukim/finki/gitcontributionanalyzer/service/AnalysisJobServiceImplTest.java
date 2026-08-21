package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisRequest;
import mk.ukim.finki.gitcontributionanalyzer.dto.ContributionAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.enums.*;
import mk.ukim.finki.gitcontributionanalyzer.exception.RepositoryException;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisJob;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import mk.ukim.finki.gitcontributionanalyzer.model.EmailDelivery;
import mk.ukim.finki.gitcontributionanalyzer.repository.InMemoryAnalysisJobRepository;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.AnalysisJobServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisJobServiceImplTest {

    @Mock
    private ReportService reportService;

    @Test
    void completesAJobAndKeepsReportsAvailableWhenEmailIsDisabled() {
        InMemoryAnalysisJobRepository repository = new InMemoryAnalysisJobRepository();
        AnalysisReport report = sampleReport(new EmailDelivery(
                EmailDeliveryStatus.DISABLED,
                "Email delivery is disabled."
        ));
        when(reportService.createReport(any(), any(AnalysisProgressListener.class)))
                .thenAnswer(invocation -> {
                    AnalysisProgressListener listener = invocation.getArgument(1);
                    listener.onStage(AnalysisStage.READING_REPOSITORY);
                    listener.onStage(AnalysisStage.ANALYZING_WITH_GEMINI);
                    listener.onStage(AnalysisStage.SAVING_REPORT);
                    listener.onStage(AnalysisStage.DELIVERING_EMAIL);
                    return report;
                });
        AnalysisJobServiceImpl service = new AnalysisJobServiceImpl(
                reportService,
                repository,
                Runnable::run
        );

        var completed = service.startAnalysis(validRequest());

        assertThat(completed.status()).isEqualTo(AnalysisJobStatus.COMPLETED);
        assertThat(completed.stage()).isEqualTo(AnalysisStage.COMPLETED);
        assertThat(completed.progress()).isEqualTo(100);
        assertThat(completed.reportId()).isEqualTo(report.id());
        assertThat(completed.errorMessage()).isNull();
    }

    @Test
    void completesAJobWhenEmailDeliveryFailedAfterTheReportWasSaved() {
        InMemoryAnalysisJobRepository repository = new InMemoryAnalysisJobRepository();
        AnalysisReport report = sampleReport(new EmailDelivery(
                EmailDeliveryStatus.FAILED,
                "The report is available on screen, but email delivery failed."
        ));
        when(reportService.createReport(any(), any(AnalysisProgressListener.class)))
                .thenReturn(report);
        AnalysisJobServiceImpl service = new AnalysisJobServiceImpl(
                reportService,
                repository,
                Runnable::run
        );

        var completed = service.startAnalysis(validRequest());

        assertThat(completed.status()).isEqualTo(AnalysisJobStatus.COMPLETED);
        assertThat(completed.progress()).isEqualTo(100);
        assertThat(completed.reportId()).isEqualTo(report.id());
    }

    @Test
    void persistsEveryRealFallbackProgressUpdateEvenWhenTheWorkerFinishesBetweenPolls() {
        InMemoryAnalysisJobRepository repository = new InMemoryAnalysisJobRepository();
        CapturingExecutor executor = new CapturingExecutor();
        AnalysisReport report = sampleReport(
                new EmailDelivery(EmailDeliveryStatus.DISABLED, "Email delivery is disabled."),
                AnalysisSource.LOCAL_FALLBACK
        );
        List<AnalysisJob> snapshots = new ArrayList<>();
        AtomicReference<UUID> jobId = new AtomicReference<>();
        when(reportService.createReport(any(), any(AnalysisProgressListener.class)))
                .thenAnswer(invocation -> {
                    AnalysisProgressListener listener = invocation.getArgument(1);
                    UUID currentJobId = jobId.get();
                    snapshots.add(repository.findById(currentJobId).orElseThrow());
                    listener.onStage(AnalysisStage.READING_REPOSITORY);
                    snapshots.add(repository.findById(currentJobId).orElseThrow());
                    listener.onStage(AnalysisStage.ANALYZING_WITH_GEMINI);
                    snapshots.add(repository.findById(currentJobId).orElseThrow());
                    listener.onStage(AnalysisStage.LOCAL_FALLBACK);
                    listener.onAnalysisSource(AnalysisSource.LOCAL_FALLBACK);
                    snapshots.add(repository.findById(currentJobId).orElseThrow());
                    listener.onStage(AnalysisStage.PREPARING_REPORT);
                    snapshots.add(repository.findById(currentJobId).orElseThrow());
                    listener.onStage(AnalysisStage.SAVING_REPORT);
                    snapshots.add(repository.findById(currentJobId).orElseThrow());
                    listener.onStage(AnalysisStage.DELIVERING_EMAIL);
                    snapshots.add(repository.findById(currentJobId).orElseThrow());
                    return report;
                });
        AnalysisJobServiceImpl service = new AnalysisJobServiceImpl(reportService, repository, executor);

        AnalysisJob queued = service.startAnalysis(validRequest());
        jobId.set(queued.id());
        executor.runTask();
        AnalysisJob completed = service.findById(queued.id()).orElseThrow();

        assertThat(snapshots)
                .extracting(AnalysisJob::stage)
                .containsExactly(
                        AnalysisStage.STARTING,
                        AnalysisStage.READING_REPOSITORY,
                        AnalysisStage.ANALYZING_WITH_GEMINI,
                        AnalysisStage.LOCAL_FALLBACK,
                        AnalysisStage.PREPARING_REPORT,
                        AnalysisStage.SAVING_REPORT,
                        AnalysisStage.DELIVERING_EMAIL
                );
        assertThat(snapshots)
                .extracting(AnalysisJob::progress)
                .containsExactly(5, 10, 55, 70, 84, 89, 95);
        assertThat(completed.progress()).isEqualTo(100);
        assertThat(completed.analysisSource()).isEqualTo(AnalysisSource.LOCAL_FALLBACK);
        assertThat(completed.stageHistory()).containsExactly(
                AnalysisStage.QUEUED,
                AnalysisStage.STARTING,
                AnalysisStage.READING_REPOSITORY,
                AnalysisStage.ANALYZING_WITH_GEMINI,
                AnalysisStage.LOCAL_FALLBACK,
                AnalysisStage.PREPARING_REPORT,
                AnalysisStage.SAVING_REPORT,
                AnalysisStage.DELIVERING_EMAIL,
                AnalysisStage.COMPLETED
        );
    }

    @Test
    void queuesTheImmutableRequestWithoutMakingADefensiveCopy() {
        InMemoryAnalysisJobRepository repository = new InMemoryAnalysisJobRepository();
        CapturingExecutor executor = new CapturingExecutor();
        AnalysisReport report = sampleReport(new EmailDelivery(EmailDeliveryStatus.SENT, "Sent."));
        when(reportService.createReport(any(), any(AnalysisProgressListener.class))).thenReturn(report);
        AnalysisJobServiceImpl service = new AnalysisJobServiceImpl(reportService, repository, executor);
        AnalysisRequest request = validRequest();

        var queued = service.startAnalysis(request);
        assertThat(queued.repositoryLabel()).isEqualTo("team/project");
        executor.runTask();

        ArgumentCaptor<AnalysisRequest> requestCaptor = ArgumentCaptor.forClass(AnalysisRequest.class);
        verify(reportService).createReport(
                requestCaptor.capture(),
                any(AnalysisProgressListener.class)
        );
        assertThat(requestCaptor.getValue()).isSameAs(request);
        assertThat(service.findById(queued.id())).get()
                .extracting(job -> job.status())
                .isEqualTo(AnalysisJobStatus.COMPLETED);
    }

    @Test
    void hidesGitProcessDetailsFromTheProgressPage() {
        InMemoryAnalysisJobRepository repository = new InMemoryAnalysisJobRepository();
        when(reportService.createReport(any(), any(AnalysisProgressListener.class)))
                .thenThrow(new RepositoryException(
                        "Cannot read changes. Git message: fatal /private/tmp/secret-repository"
                ));
        AnalysisJobServiceImpl service = new AnalysisJobServiceImpl(
                reportService,
                repository,
                Runnable::run
        );

        var failed = service.startAnalysis(validRequest());

        assertThat(failed.status()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(failed.errorMessage())
                .contains("repository could not be read")
                .doesNotContain("fatal")
                .doesNotContain("/private/tmp");
        assertThat(failed.reportId()).isNull();
    }

    @Test
    void hidesUnexpectedInternalFailureDetailsFromTheBrowser() {
        InMemoryAnalysisJobRepository repository = new InMemoryAnalysisJobRepository();
        when(reportService.createReport(any(), any(AnalysisProgressListener.class)))
                .thenThrow(new IllegalStateException("secret internal detail"));
        AnalysisJobServiceImpl service = new AnalysisJobServiceImpl(
                reportService,
                repository,
                Runnable::run
        );

        var failed = service.startAnalysis(validRequest());

        assertThat(failed.status()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(failed.errorMessage())
                .contains("could not be completed")
                .doesNotContain("secret internal detail");
    }

    @Test
    void marksARejectedJobAsFailedWithoutRunningTheReportService() {
        InMemoryAnalysisJobRepository repository = new InMemoryAnalysisJobRepository();
        Executor rejectingExecutor = ignored -> {
            throw new TaskRejectedException("queue full");
        };
        AnalysisJobServiceImpl service = new AnalysisJobServiceImpl(
                reportService,
                repository,
                rejectingExecutor
        );

        var failed = service.startAnalysis(validRequest());

        assertThat(failed.status()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(failed.progress()).isZero();
        assertThat(failed.errorMessage()).contains("queue").contains("try again");
        verify(reportService, never()).createReport(any(), any(AnalysisProgressListener.class));
    }

    @Test
    void evictsTheOldestTerminalJobWhenANewQueuedJobExceedsTheLimit() {
        InMemoryAnalysisJobRepository repository = new InMemoryAnalysisJobRepository();
        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        UUID oldestId = UUID.randomUUID();
        UUID secondOldestId = UUID.randomUUID();

        for (int index = 0; index < InMemoryAnalysisJobRepository.MAX_RETAINED_JOBS; index++) {
            UUID id = index == 0 ? oldestId : index == 1 ? secondOldestId : UUID.randomUUID();
            OffsetDateTime timestamp = start.plusSeconds(index);
            repository.save(AnalysisJob.queued(id, timestamp));
        }

        repository.update(oldestId, job -> job.fail("Finished test job.", start));
        repository.update(secondOldestId, job -> job.fail("Finished test job.", start.plusSeconds(1)));

        UUID newestId = UUID.randomUUID();
        repository.save(AnalysisJob.queued(
                newestId,
                start.plusSeconds(InMemoryAnalysisJobRepository.MAX_RETAINED_JOBS)
        ));

        assertThat(repository.findById(oldestId)).isEmpty();
        assertThat(repository.findById(secondOldestId)).isPresent();
        assertThat(repository.findById(newestId)).isPresent();
    }

    @Test
    void evictsANewTerminalJobWhenOnlyQueuedOverflowExisted() {
        InMemoryAnalysisJobRepository repository = new InMemoryAnalysisJobRepository();
        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        UUID firstId = UUID.randomUUID();

        for (int index = 0; index <= InMemoryAnalysisJobRepository.MAX_RETAINED_JOBS; index++) {
            UUID id = index == 0 ? firstId : UUID.randomUUID();
            repository.save(AnalysisJob.queued(id, start.plusSeconds(index)));
        }

        assertThat(repository.findById(firstId)).isPresent();

        repository.update(firstId, job -> job.fail("Finished test job.", start));

        assertThat(repository.findById(firstId)).isEmpty();
    }

    private AnalysisRequest validRequest() {
        return new AnalysisRequest(
                "https://github.com/team/project",
                "A team planning application with shared tasks and progress tracking.",
                "mentor@example.com"
        );
    }

    private AnalysisReport sampleReport(EmailDelivery delivery) {
        return sampleReport(delivery, AnalysisSource.GEMINI);
    }

    private AnalysisReport sampleReport(EmailDelivery delivery, AnalysisSource source) {
        return new AnalysisReport(
                UUID.randomUUID(),
                "https://github.com/team/project",
                "project",
                "main",
                "A team planning application with shared tasks and progress tracking.",
                "mentor@example.com",
                source,
                source == AnalysisSource.GEMINI ? "gemini-test" : "Built-in heuristic rules",
                source == AnalysisSource.GEMINI
                        ? "Gemini completed the analysis."
                        : "The built-in local analyzer completed the analysis.",
                1,
                OffsetDateTime.now(),
                new ContributionAnalysis(
                        "Summary",
                        "Goal alignment",
                        List.of(),
                        List.of(),
                        "Conclusion",
                        "Methodology"
                ),
                delivery
        );
    }

    private static class CapturingExecutor implements Executor {

        private Runnable task;

        @Override
        public void execute(Runnable command) {
            task = command;
        }

        void runTask() {
            task.run();
        }
    }
}