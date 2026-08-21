package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.exception.RepositoryException;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.GitRepositoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GitRepositoryServiceImplTest {

    private AppSettings settings;
    private GitRepositoryServiceImpl service;

    @BeforeEach
    void setUp() {
        settings = new AppSettings(
                80,
                6000,
                30,
                "",
                "gemini-3.7-flash",
                180,
                false,
                ""
        );
        service = new GitRepositoryServiceImpl(settings);
    }

    @Test
    void acceptsGithubAndGitlabHttpsUrls() {
        assertThat(service.validateUrl("https://github.com/team/project").getHost())
                .isEqualTo("github.com");
        assertThat(service.validateUrl("https://gitlab.com/group/subgroup/project.git").getHost())
                .isEqualTo("gitlab.com");
    }

    @Test
    void keepsTheOwnerAndNestedGroupInTheRepositoryLabel() {
        assertThat(service.repositoryName(URI.create("https://github.com/orbital-labs/flightdeck.git")))
                .isEqualTo("orbital-labs/flightdeck");
        assertThat(service.repositoryName(URI.create("https://gitlab.com/group/subgroup/project")))
                .isEqualTo("group/subgroup/project");
    }

    @Test
    void rejectsUnknownHostsAndNonHttpsUrls() {
        assertThatThrownBy(() -> service.validateUrl("https://example.com/team/project"))
                .isInstanceOf(RepositoryException.class);
        assertThatThrownBy(() -> service.validateUrl("git@github.com:team/project.git"))
                .isInstanceOf(RepositoryException.class);
        assertThatThrownBy(() -> service.validateUrl("https://github.com/team/project?token=secret"))
                .isInstanceOf(RepositoryException.class);
    }

    @Test
    void usesACompleteCloneSoCommitDiffObjectsAreAvailable() {
        var command = service.cloneCommand(
                "https://github.com/team/project",
                Path.of("temporary-repository")
        );

        assertThat(command)
                .contains("--no-checkout")
                .doesNotContain("--filter=blob:none");
    }

    @Test
    void terminatesTheGitProcessTreeWhenTheOperationTimesOut() throws InterruptedException {
        Process process = mock(Process.class);
        ProcessHandle descendant = mock(ProcessHandle.class);
        ProcessHandle grandchild = mock(ProcessHandle.class);
        when(process.waitFor(30, TimeUnit.SECONDS)).thenReturn(false);
        when(process.descendants()).thenReturn(Stream.of(descendant, grandchild));

        assertThatThrownBy(() -> service.waitForCompletion(process))
                .isInstanceOf(RepositoryException.class)
                .hasMessage("The Git operation timed out and was stopped.");

        verify(descendant).destroyForcibly();
        verify(grandchild).destroyForcibly();
        verify(process).destroyForcibly();
    }

    @Test
    void terminatesTheGitProcessTreeAndRestoresTheInterruptFlag() throws InterruptedException {
        Process process = mock(Process.class);
        ProcessHandle descendant = mock(ProcessHandle.class);
        ProcessHandle grandchild = mock(ProcessHandle.class);
        when(process.waitFor(30, TimeUnit.SECONDS)).thenThrow(new InterruptedException("shutdown"));
        when(process.descendants()).thenReturn(Stream.of(descendant, grandchild));

        try {
            assertThatThrownBy(() -> service.waitForCompletion(process))
                    .isInstanceOf(RepositoryException.class)
                    .hasMessage("The Git operation was interrupted.")
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }

        verify(descendant).destroyForcibly();
        verify(grandchild).destroyForcibly();
        verify(process).destroyForcibly();
    }

    @Test
    void leavesACompletedGitProcessUntouched() throws InterruptedException {
        Process process = mock(Process.class);
        when(process.waitFor(30, TimeUnit.SECONDS)).thenReturn(true);

        service.waitForCompletion(process);

        verify(process, never()).descendants();
        verify(process, never()).destroyForcibly();
    }
}