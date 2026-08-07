package mk.ukim.finki.gitcontributionanalyzer.exception;

public class AnalysisJobNotFoundException extends RuntimeException {
    public AnalysisJobNotFoundException(String message) {
        super(message);
    }
}