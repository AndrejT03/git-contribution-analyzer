package mk.ukim.finki.gitcontributionanalyzer.service.impl;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisRequest;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisSource;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStage;
import mk.ukim.finki.gitcontributionanalyzer.exception.RepositoryException;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisJob;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import mk.ukim.finki.gitcontributionanalyzer.repository.AnalysisJobRepository;
import mk.ukim.finki.gitcontributionanalyzer.service.AnalysisJobService;
import mk.ukim.finki.gitcontributionanalyzer.service.AnalysisProgressListener;
import mk.ukim.finki.gitcontributionanalyzer.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.net.URI;
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
        OffsetDateTime now = OffsetDateTime.now();
        AnalysisJob queuedJob = AnalysisJob.queued(
                UUID.randomUUID(),
                repositoryLabel(request.repositoryUrl()),
                now
        );
        jobRepository.save(queuedJob);

        try {
            taskExecutor.execute(() -> runAnalysis(queuedJob.id(), request));
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
                    progressListener(jobId)
            );
            jobRepository.update(
                    jobId,
                    job -> job.complete(report.id(), report.analysisSource(), OffsetDateTime.now())
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

    private void updateAnalysisSource(UUID jobId, AnalysisSource source) {
        jobRepository.update(
                jobId,
                job -> job.selectAnalysisSource(source, OffsetDateTime.now())
        );
    }

    private AnalysisProgressListener progressListener(UUID jobId) {
        return new AnalysisProgressListener() {
            @Override
            public void onStage(AnalysisStage stage) {
                updateStage(jobId, stage);
            }

            @Override
            public void onAnalysisSource(AnalysisSource source) {
                updateAnalysisSource(jobId, source);
            }
        };
    }

    private String repositoryLabel(String repositoryUrl) {
        try {
            String path = URI.create(repositoryUrl).getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                return "Repository analysis";
            }
            return path
                    .replaceFirst("^/", "")
                    .replaceFirst("/$", "")
                    .replaceFirst("\\.git$", "");
        } catch (IllegalArgumentException exception) {
            return "Repository analysis";
        }
    }
}
