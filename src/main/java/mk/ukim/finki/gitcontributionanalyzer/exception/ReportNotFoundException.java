package mk.ukim.finki.gitcontributionanalyzer.exception;

public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException(String message) {
        super(message);
    }
}