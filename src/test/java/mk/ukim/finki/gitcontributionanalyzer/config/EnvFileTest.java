package mk.ukim.finki.gitcontributionanalyzer.config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class EnvFileTest {

    @TempDir
    Path tempDirectory;

    @Test
    void readsValuesOnlyFromDotEnvFile() throws IOException {
        Path envPath = tempDirectory.resolve(".env");
        Files.writeString(envPath, """
                # comment
                GEMINI_API_KEY="secret-key"
                MAX_COMMITS=55
                MAIL_ENABLED=true
                """);

        EnvFile envFile = new EnvFile(envPath);

        assertThat(envFile.get("GEMINI_API_KEY")).isEqualTo("secret-key");
        assertThat(envFile.getInt("MAX_COMMITS", 80, 1, 200)).isEqualTo(55);
        assertThat(envFile.getBoolean("MAIL_ENABLED", false)).isTrue();
    }

    @Test
    void usesDefaultsWhenFileDoesNotExist() {
        EnvFile envFile = new EnvFile(tempDirectory.resolve("missing.env"));

        assertThat(envFile.get("GEMINI_API_KEY")).isNull();
        assertThat(envFile.getInt("MAX_COMMITS", 80, 1, 200)).isEqualTo(80);
        assertThat(envFile.getBoolean("MAIL_ENABLED", false)).isFalse();
    }
}
