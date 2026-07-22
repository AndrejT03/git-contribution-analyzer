package mk.ukim.finki.gitcontributionanalyzer.config;
import org.springframework.stereotype.Component;

@Component
public class AppSettings {
    private final EnvFile envFile;

    public AppSettings(EnvFile envFile) {
        this.envFile = envFile;
    }

    public int maxCommits() {
        return envFile.getInt("MAX_COMMITS", 80, 1, 200);
    }
    public int maxDiffChars() {
        return envFile.getInt("MAX_DIFF_CHARS", 6000, 500, 20000);
    }
    public int gitTimeoutSeconds() {
        return envFile.getInt("GIT_TIMEOUT_SECONDS", 120, 20, 600);
    }
}