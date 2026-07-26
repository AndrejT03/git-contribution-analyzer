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

    public int gitTimeoutSeconds() { return envFile.getInt("GIT_TIMEOUT_SECONDS", 120, 20, 600); }

    public String geminiApiKey() { return envFile.getOrDefault("GEMINI_API_KEY", "").trim(); }

    public String geminiModel() { return envFile.getOrDefault("GEMINI_MODEL", "gemini-3.6-flash").trim(); }

    public int geminiTimeoutSeconds() { return envFile.getInt("GEMINI_TIMEOUT_SECONDS", 180, 30, 600); }

    public boolean mailEnabled() {
        return envFile.getBoolean("MAIL_ENABLED", false);
    }

    public String mailHost() {
        return envFile.getOrDefault("MAIL_HOST", "smtp.gmail.com").trim();
    }

    public int mailPort() {
        return envFile.getInt("MAIL_PORT", 587, 1, 65535);
    }

    public String mailUsername() {
        return envFile.getOrDefault("MAIL_USERNAME", "").trim();
    }

    public String mailPassword() {
        return envFile.getOrDefault("MAIL_PASSWORD", "");
    }

    public String mailFrom() {
        return envFile.getOrDefault("MAIL_FROM", mailUsername()).trim();
    }
}