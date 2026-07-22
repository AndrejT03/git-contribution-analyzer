package mk.ukim.finki.gitcontributionanalyzer.config;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EnvFile {

    private final Map<String, String> values;

    public EnvFile() {
        this(Path.of(".env"));
    }

    EnvFile(Path file) {
        values = Collections.unmodifiableMap(readFile(file));
    }

    public String get(String key) {
        return values.get(key);
    }

    public String getOrDefault(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue, int min, int max) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            int number = Integer.parseInt(value);
            return Math.max(min, Math.min(number, max));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private Map<String, String> readFile(Path file) {
        if (!Files.exists(file)) {
            return new HashMap<>();
        }

        try {
            List<String> lines = Files.readAllLines(file);
            Map<String, String> result = new HashMap<>();

            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }

                int separator = trimmedLine.indexOf('=');
                if (separator < 1) {
                    continue;
                }

                String key = trimmedLine.substring(0, separator).trim();
                String value = trimmedLine.substring(separator + 1).trim();
                result.put(key, removeQuotes(value));
            }

            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read the .env file.", exception);
        }
    }

    private String removeQuotes(String value) {
        if (value.length() >= 2) {
            boolean doubleQuotes = value.startsWith("\"") && value.endsWith("\"");
            boolean singleQuotes = value.startsWith("'") && value.endsWith("'");
            if (doubleQuotes || singleQuotes) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}