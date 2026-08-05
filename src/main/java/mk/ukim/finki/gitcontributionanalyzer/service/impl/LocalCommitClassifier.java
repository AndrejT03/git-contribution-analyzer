package mk.ukim.finki.gitcontributionanalyzer.service.impl;
import mk.ukim.finki.gitcontributionanalyzer.dto.CommitAnalysis;
import mk.ukim.finki.gitcontributionanalyzer.enums.CommitCategory;
import mk.ukim.finki.gitcontributionanalyzer.model.ChangedFile;
import mk.ukim.finki.gitcontributionanalyzer.model.GitCommit;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LocalCommitClassifier {

    private static final Set<String> PROJECT_STOP_WORDS = Set.of(
            "application", "project", "system", "team", "that", "this",
            "with", "from", "using", "user"
    );

    public CommitAnalysis analyze(GitCommit commit, String projectDescription) {
        CommitCategory category = classify(commit);
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

    private CommitCategory classify(GitCommit commit) {
        if (commit.changedFiles().isEmpty()) {
            return CommitCategory.OTHER;
        }

        String message = commit.message().toLowerCase(Locale.ROOT);

        if (commit.changedFiles().stream().allMatch(this::isDocumentationFile)) {
            return CommitCategory.DOCUMENTATION;
        }
        if (commit.changedFiles().stream().allMatch(this::isGeneratedFile)) {
            return CommitCategory.OTHER;
        }
        if (containsWord(message, "format", "formatting", "whitespace", "indent")) {
            return CommitCategory.FORMATTING;
        }
        if (containsWord(message, "test", "tests", "testing", "spec")
                || commit.changedFiles().stream().anyMatch(this::isTestFile)) {
            return CommitCategory.TESTING;
        }
        if (containsWord(message, "fix", "fixed", "fixes", "bug", "repair", "hotfix")) {
            return CommitCategory.BUG_FIX;
        }
        if (containsWord(message, "refactor", "cleanup", "restructure", "rename")) {
            return CommitCategory.REFACTORING;
        }
        if (containsWord(message, "config", "setup", "dependency", "dependencies")
                || commit.changedFiles().stream().allMatch(this::isConfigurationFile)) {
            return CommitCategory.CONFIGURATION;
        }
        return CommitCategory.FUNCTIONAL;
    }

    private int calculateImportance(
            GitCommit commit,
            String projectDescription,
            CommitCategory category) {
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
        if (category == CommitCategory.DOCUMENTATION || category == CommitCategory.FORMATTING) {
            importance = Math.min(2, importance);
        }
        if (category == CommitCategory.CONFIGURATION) {
            importance = Math.min(3, importance);
        }
        if (commit.changedFiles().stream().allMatch(this::isGeneratedFile)) {
            importance = 1;
        }
        return importance;
    }

    private boolean matchesProjectKeyword(GitCommit commit, String projectDescription) {
        String commitText = commit.message() + " " + commit.changedFiles().stream()
                .map(ChangedFile::path)
                .reduce("", (left, right) -> left + " " + right);
        Set<String> commitWords = words(commitText);

        return words(projectDescription).stream()
                .filter(word -> word.length() >= 4)
                .filter(word -> !PROJECT_STOP_WORDS.contains(word))
                .anyMatch(commitWords::contains);
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
                || path.endsWith("build.gradle.kts")
                || path.endsWith("package-lock.json")
                || path.endsWith("yarn.lock")
                || path.endsWith("pnpm-lock.yaml")
                || path.endsWith("gradle.lockfile")
                || path.endsWith(".properties")
                || path.endsWith(".yml")
                || path.endsWith(".yaml")
                || path.startsWith(".github/");
    }

    private boolean isGeneratedFile(ChangedFile file) {
        String path = "/" + file.path().toLowerCase(Locale.ROOT);
        return path.contains("/generated/")
                || path.contains("/vendor/")
                || path.endsWith(".min.js")
                || path.endsWith(".min.css")
                || path.endsWith(".map");
    }

    private boolean isTestFile(ChangedFile file) {
        String path = "/" + file.path().toLowerCase(Locale.ROOT);
        return path.contains("/test/")
                || path.contains("/tests/")
                || path.endsWith("test.java")
                || path.endsWith("tests.java")
                || path.contains(".test.")
                || path.contains(".spec.");
    }

    private boolean containsWord(String text, String... expectedWords) {
        Set<String> actualWords = words(text);
        return Arrays.stream(expectedWords).anyMatch(actualWords::contains);
    }

    private Set<String> words(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> !word.isBlank())
                .collect(Collectors.toSet());
    }
}