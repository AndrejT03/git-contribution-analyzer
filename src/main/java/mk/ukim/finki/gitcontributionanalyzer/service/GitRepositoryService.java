package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.exception.RepositoryException;
import mk.ukim.finki.gitcontributionanalyzer.model.ChangedFile;
import mk.ukim.finki.gitcontributionanalyzer.model.GitCommit;
import mk.ukim.finki.gitcontributionanalyzer.model.RepositoryData;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class GitRepositoryService {

    private static final String RECORD_SEPARATOR = "\u001e";
    private static final String FIELD_SEPARATOR = "\u001f";

    private final AppSettings settings;

    public GitRepositoryService(AppSettings settings) {
        this.settings = settings;
    }

    public RepositoryData readRepository(String repositoryUrl) {
        URI uri = validateUrl(repositoryUrl);
        Path workDirectory = null;

        try {
            workDirectory = Files.createTempDirectory("git-contribution-");
            Path repositoryDirectory = workDirectory.resolve("repository");
            cloneRepository(uri.toString(), repositoryDirectory);

            String branch = readDefaultBranch(repositoryDirectory);
            List<GitCommit> commits = readCommits(repositoryDirectory);
            if (commits.isEmpty()) {
                throw new RepositoryException("The repository has no commit history.");
            }

            return new RepositoryData(
                    uri.toString(),
                    repositoryName(uri),
                    branch,
                    commits
            );
        } catch (IOException exception) {
            throw new RepositoryException("Cannot prepare a temporary repository directory.", exception);
        } finally {
            deleteDirectory(workDirectory);
        }
    }

    URI validateUrl(String repositoryUrl) {
        try {
            URI uri = new URI(repositoryUrl.trim());
            String host = uri.getHost();
            boolean allowedHost = "github.com".equalsIgnoreCase(host)
                    || "gitlab.com".equalsIgnoreCase(host);
            boolean validPath = uri.getPath() != null
                    && uri.getPath().split("/").length >= 3;

            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !allowedHost
                    || !validPath
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new RepositoryException("Enter a valid HTTPS URL from GitHub or GitLab.");
            }

            return uri;
        } catch (URISyntaxException | NullPointerException exception) {
            throw new RepositoryException("The repository URL is invalid.");
        }
    }

    private void cloneRepository(String url, Path destination) {
        runCommand(List.of(
                "git",
                "-c", "http.lowSpeedLimit=1000",
                "-c", "http.lowSpeedTime=30",
                "clone",
                "--quiet",
                "--no-checkout",
                "--filter=blob:none",
                url,
                destination.toString()
        ), null, "The repository could not be cloned. Check that it is public and available.");
    }

    private String readDefaultBranch(Path repositoryDirectory) {
        try {
            String branch = runGit(repositoryDirectory,
                    List.of("rev-parse", "--abbrev-ref", "origin/HEAD"),
                    "Cannot read the default branch.").trim();
            return branch.replaceFirst("^origin/", "");
        } catch (RepositoryException exception) {
            return "unknown";
        }
    }

    private List<GitCommit> readCommits(Path repositoryDirectory) {
        String format = "%H%x1f%an%x1f%ae%x1f%aI%x1f%B%x1e";
        String output = runGit(repositoryDirectory, List.of(
                "log",
                "--all",
                "--max-count=" + settings.maxCommits(),
                "--date=iso-strict",
                "--pretty=format:" + format
        ), "Cannot read the commit history.");

        List<GitCommit> commits = new ArrayList<>();
        for (String record : output.split(RECORD_SEPARATOR)) {
            String cleanRecord = record.strip();
            if (cleanRecord.isEmpty()) {
                continue;
            }

            String[] fields = cleanRecord.split(FIELD_SEPARATOR, 5);
            if (fields.length < 5) {
                continue;
            }

            try {
                String hash = fields[0].trim();
                commits.add(new GitCommit(
                        hash,
                        fields[1].trim(),
                        fields[2].trim(),
                        OffsetDateTime.parse(fields[3].trim()),
                        fields[4].trim(),
                        readChangedFiles(repositoryDirectory, hash),
                        readDiff(repositoryDirectory, hash)
                ));
            } catch (DateTimeParseException exception) {
                throw new RepositoryException("A commit with an invalid date was found.", exception);
            }
        }
        return commits;
    }

    private List<ChangedFile> readChangedFiles(Path repositoryDirectory, String hash) {
        String output = runGit(repositoryDirectory, List.of(
                "-c", "core.quotepath=false",
                "show", "--format=", "--numstat", "--find-renames", hash
        ), "Cannot read the changed files.");

        List<ChangedFile> files = new ArrayList<>();
        for (String line : output.lines().toList()) {
            String[] parts = line.split("\t", 3);
            if (parts.length == 3) {
                files.add(new ChangedFile(
                        parts[2],
                        parseNumber(parts[0]),
                        parseNumber(parts[1])
                ));
            }
        }
        return files;
    }

    private String readDiff(Path repositoryDirectory, String hash) {
        String diff = runGit(repositoryDirectory, List.of(
                "-c", "core.quotepath=false",
                "show", "--format=", "--find-renames", "--unified=2", "--no-ext-diff", hash
        ), "Cannot read the commit changes.");

        int limit = settings.maxDiffChars();
        if (diff.length() <= limit) {
            return diff;
        }
        return diff.substring(0, limit) + "\n... diff was truncated ...";
    }

    private String runGit(Path repositoryDirectory, List<String> arguments, String errorMessage) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repositoryDirectory.toString());
        command.addAll(arguments);
        return runCommand(command, repositoryDirectory, errorMessage);
    }

    private String runCommand(List<String> command, Path directory, String errorMessage) {
        Path outputFile = null;
        try {
            outputFile = Files.createTempFile("git-command-", ".log");
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile());
            if (directory != null) {
                processBuilder.directory(directory.toFile());
            }

            Process process = processBuilder.start();
            boolean finished = process.waitFor(settings.gitTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RepositoryException("The Git operation timed out and was stopped.");
            }

            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new RepositoryException(errorMessage + " Git message: " + shortMessage(output));
            }
            return output;
        } catch (IOException exception) {
            throw new RepositoryException("Git is not available on this computer.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RepositoryException("The Git operation was interrupted.", exception);
        } finally {
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException ignored) {
                    // Temporary command output will be cleaned by the operating system.
                }
            }
        }
    }

    private int parseNumber(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String shortMessage(String output) {
        String clean = output.strip().replaceAll("\\s+", " ");
        return clean.length() <= 220 ? clean : clean.substring(0, 220) + "...";
    }

    private String repositoryName(URI uri) {
        String path = uri.getPath().replaceFirst("/$", "");
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.endsWith(".git") ? name.substring(0, name.length() - 4) : name;
    }

    private void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // The operating system can remove a leftover temporary directory later.
                }
            });
        } catch (IOException ignored) {
            // A cleanup problem should not replace the analysis result.
        }
    }
}