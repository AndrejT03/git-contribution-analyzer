package mk.ukim.finki.gitcontributionanalyzer.model;

public record ChangedFile(
        String path,
        int additions,
        int deletions
) {
}