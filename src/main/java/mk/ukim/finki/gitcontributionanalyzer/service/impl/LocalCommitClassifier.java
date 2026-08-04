package mk.ukim.finki.gitcontributionanalyzer.service.impl;
import mk.ukim.finki.gitcontributionanalyzer.dto.CommitAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.model.ChangedFile;
import mk.ukim.finki.gitcontributionanalyzer.model.GitCommit;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class LocalCommitClassifier {

    public CommitAnalysis analyze(GitCommit commit, String projectDescription) {
        String category = classify(commit);
        int importance = calculateImportance(commit, projectDescription, category);
        int changedLines = changedLines(commit);

        String explanation = "Local rules classified this commit as " + category
                + " from its message and changed file paths. "
                + changedLines + " changed lines produced importance " + importance + "/5.";

        return new CommitAnalysis(
                commit.hash(),
                commit.message(),
                category,
                importance,
                explanation
        );
    }

    private String classify(GitCommit commit) {
        if (commit.changedFiles().isEmpty()) {
            return "OTHER";
        }

        String message = commit.message().toLowerCase(Locale.ROOT);
        String paths = commit.changedFiles().stream()
                .map(ChangedFile::path)
                .map(path -> path.toLowerCase(Locale.ROOT))
                .reduce("", (left, right) -> left + " " + right);

        if (commit.changedFiles().stream().allMatch(this::isDocumentationFile)) {
            return "DOCUMENTATION";
        }
        if (containsAny(message, "format", "formatting", "whitespace", "indent")) {
            return "FORMATTING";
        }
        if (containsAny(message + paths, "test", "tests", "testing", "spec")) {
            return "TESTING";
        }
        if (containsAny(message, "fix", "bug", "repair", "hotfix")) {
            return "BUG_FIX";
        }
        if (containsAny(message, "refactor", "cleanup", "restructure", "rename")) {
            return "REFACTORING";
        }
        if (containsAny(message, "config", "setup", "dependency", "dependencies")
                || commit.changedFiles().stream().allMatch(this::isConfigurationFile)) {
            return "CONFIGURATION";
        }
        return "FUNCTIONAL";
    }

    private int calculateImportance(GitCommit commit, String projectDescription, String category) {
        int lines = changedLines(commit);
        int importance;

        if (lines <= 10) {
            importance = 1;
        } else if (lines <= 50) {
            importance = 2;
        } else if (lines <= 150) {
            importance = 3;
        } else if (lines <= 400) {
            importance = 4;
        } else {
            importance = 5;
        }

        if (matchesProjectKeyword(commit, projectDescription)) {
            importance = Math.min(5, importance + 1);
        }
        if ("DOCUMENTATION".equals(category) || "FORMATTING".equals(category)) {
            importance = Math.min(2, importance);
        }
        if ("CONFIGURATION".equals(category)) {
            importance = Math.min(3, importance);
        }
        return importance;
    }

    private boolean matchesProjectKeyword(GitCommit commit, String projectDescription) {
        String commitText = (commit.message() + " " + commit.changedFiles().stream()
                .map(ChangedFile::path)
                .reduce("", (left, right) -> left + " " + right))
                .toLowerCase(Locale.ROOT);

        return Arrays.stream(projectDescription.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> word.length() >= 4)
                .anyMatch(commitText::contains);
    }

    private int changedLines(GitCommit commit) {
        return commit.changedFiles().stream()
                .mapToInt(file -> file.additions() + file.deletions())
                .sum();
    }

    private boolean isDocumentationFile(ChangedFile file) {
        String path = file.path().toLowerCase(Locale.ROOT);
        return path.endsWith(".md") || path.startsWith("docs/") || path.contains("/docs/");
    }

    private boolean isConfigurationFile(ChangedFile file) {
        String path = file.path().toLowerCase(Locale.ROOT);
        List<String> names = List.of("pom.xml", "build.gradle", ".env.example");
        return names.contains(path)
                || path.endsWith(".properties")
                || path.endsWith(".yml")
                || path.endsWith(".yaml")
                || path.startsWith(".github/");
    }

    private boolean containsAny(String text, String... words) {
        return Arrays.stream(words).anyMatch(text::contains);
    }
}