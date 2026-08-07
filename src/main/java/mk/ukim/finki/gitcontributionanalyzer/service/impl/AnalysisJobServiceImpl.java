package mk.ukim.finki.gitcontributionanalyzer.service.impl;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisRequest;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStage;
import mk.ukim.finki.gitcontributionanalyzer.exception.RepositoryException;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisJob;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import mk.ukim.finki.gitcontributionanalyzer.repository.AnalysisJobRepository;
import mk.ukim.finki.gitcontributionanalyzer.service.AnalysisJobService;
import mk.ukim.finki.gitcontributionanalyzer.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class AnalysisJobServiceImpl implements AnalysisJobService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisJobServiceImpl.class);
    private static final String QUEUE_FULL_MESSAGE =
            "The analysis queue is currently full. Please try again shortly.";
    private static final String REPOSITORY_FAILURE_MESSAGE =
            "The repository could not be read. Check that it is public and available, then try again.";
    private static final String GENERAL_FAILURE_MESSAGE =
            "The analysis could not be completed. Please try again and check the repository URL.";

    private final ReportService reportService;
    private final AnalysisJobRepository jobRepository;
    private final Executor taskExecutor;

    public AnalysisJobServiceImpl(
            ReportService reportService,
            AnalysisJobRepository jobRepository,
            @Qualifier("analysisTaskExecutor") Executor taskExecutor) {
        this.reportService = reportService;
        this.jobRepository = jobRepository;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public AnalysisJob startAnalysis(AnalysisRequest request) {
        AnalysisRequest requestCopy = copyOf(request);
        OffsetDateTime now = OffsetDateTime.now();
        AnalysisJob queuedJob = AnalysisJob.queued(UUID.randomUUID(), now);
        jobRepository.save(queuedJob);

        try {
            taskExecutor.execute(() -> runAnalysis(queuedJob.id(), requestCopy));
        } catch (TaskRejectedException exception) {
            LOGGER.warn("Analysis job {} was rejected because the worker queue is full.", queuedJob.id());
            return jobRepository.update(
                            queuedJob.id(),
                            job -> job.fail(QUEUE_FULL_MESSAGE, OffsetDateTime.now())
                    )
                    .orElse(queuedJob);
        }

        return jobRepository.findById(queuedJob.id()).orElse(queuedJob);
    }

    @Override
    public Optional<AnalysisJob> findById(UUID id) {
        return jobRepository.findById(id);
    }

    private void runAnalysis(UUID jobId, AnalysisRequest request) {
        updateStage(jobId, AnalysisStage.STARTING);

        try {
            AnalysisReport report = reportService.createReport(
                    request,
                    stage -> updateStage(jobId, stage)
            );
            jobRepository.update(
                    jobId,
                    job -> job.complete(report.id(), OffsetDateTime.now())
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Analysis job {} failed.", jobId, exception);
            String userMessage = exception instanceof RepositoryException
                    ? REPOSITORY_FAILURE_MESSAGE
                    : GENERAL_FAILURE_MESSAGE;
            jobRepository.update(
                    jobId,
                    job -> job.fail(userMessage, OffsetDateTime.now())
            );
        }
    }

    private void updateStage(UUID jobId, AnalysisStage stage) {
        jobRepository.update(
                jobId,
                job -> job.advanceTo(stage, OffsetDateTime.now())
        );
    }

    private AnalysisRequest copyOf(AnalysisRequest source) {
        AnalysisRequest copy = new AnalysisRequest();
        copy.setRepositoryUrl(source.getRepositoryUrl());
        copy.setProjectDescription(source.getProjectDescription());
        copy.setEmail(source.getEmail());
        return copy;
    }
}
