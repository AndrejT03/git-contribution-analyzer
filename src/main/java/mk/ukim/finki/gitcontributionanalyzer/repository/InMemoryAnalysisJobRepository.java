package mk.ukim.finki.gitcontributionanalyzer.repository;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisJob;
import org.springframework.stereotype.Repository;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

@Repository
public class InMemoryAnalysisJobRepository implements AnalysisJobRepository {

    public static final int MAX_RETAINED_JOBS = 200;
    private final Map<UUID, AnalysisJob> jobs = new ConcurrentHashMap<>();

    @Override
    public void save(AnalysisJob job) {
        jobs.put(job.id(), job);
        evictOldestTerminalJobs();
    }

    @Override
    public Optional<AnalysisJob> findById(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public Optional<AnalysisJob> update(UUID id, UnaryOperator<AnalysisJob> update) {
        AnalysisJob updatedJob = jobs.computeIfPresent(
                id,
                (ignored, currentJob) -> update.apply(currentJob)
        );
        evictOldestTerminalJobs();
        return Optional.ofNullable(updatedJob);
    }

    private synchronized void evictOldestTerminalJobs() {
        int overflow = jobs.size() - MAX_RETAINED_JOBS;
        if (overflow <= 0) {
            return;
        }

        jobs.values().stream()
                .filter(AnalysisJob::isTerminal)
                .sorted(Comparator.comparing(AnalysisJob::updatedAt)
                        .thenComparing(AnalysisJob::id))
                .limit(overflow)
                .forEach(job -> jobs.remove(job.id(), job));
    }
}