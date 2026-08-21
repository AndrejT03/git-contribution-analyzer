package mk.ukim.finki.gitcontributionanalyzer.config;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppSettings(
        @Min(1)
        @Max(200)
        int maxCommits,

        @Min(500)
        @Max(20000)
        int maxDiffChars,

        @Min(20)
        @Max(600)
        int gitTimeoutSeconds,

        String geminiApiKey,

        String geminiModel,

        @Min(30)
        @Max(600)
        int geminiTimeoutSeconds,

        boolean mailEnabled,

        String mailFrom
) {

    public AppSettings {
        geminiApiKey = normalized(geminiApiKey);
        geminiModel = normalized(geminiModel);
        mailFrom = normalized(mailFrom);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}