package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.exception.RepositoryException;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.GitRepositoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class GitRepositoryServiceImplTest {

    private GitRepositoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GitRepositoryServiceImpl(mock(AppSettings.class));
    }

    @Test
    void acceptsGithubAndGitlabHttpsUrls() {
        assertThat(service.validateUrl("https://github.com/team/project").getHost())
                .isEqualTo("github.com");
        assertThat(service.validateUrl("https://gitlab.com/group/subgroup/project.git").getHost())
                .isEqualTo("gitlab.com");
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
}