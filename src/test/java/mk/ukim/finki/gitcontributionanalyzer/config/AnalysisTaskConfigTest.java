package mk.ukim.finki.gitcontributionanalyzer.config;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class AnalysisTaskConfigTest {

    @Test
    void createsABoundedExecutorWithNamedWorkerThreads() throws InterruptedException {
        ThreadPoolTaskExecutor executor = new AnalysisTaskConfig().analysisTaskExecutor();
        executor.initialize();
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity())
                    .isEqualTo(20);

            AtomicReference<String> workerName = new AtomicReference<>();
            CountDownLatch completed = new CountDownLatch(1);
            executor.execute(() -> {
                workerName.set(Thread.currentThread().getName());
                completed.countDown();
            });

            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(workerName.get()).startsWith("analysis-worker-");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void interruptsInMemoryAnalysisWorkOnApplicationShutdown() throws InterruptedException {
        ThreadPoolTaskExecutor executor = new AnalysisTaskConfig().analysisTaskExecutor();
        executor.initialize();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicBoolean interruptionObserved = new AtomicBoolean();

        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException exception) {
                    interruptionObserved.set(true);
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
            });

            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();

            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(interruptionObserved).isTrue();
        } finally {
            executor.getThreadPoolExecutor().shutdownNow();
        }
    }
}