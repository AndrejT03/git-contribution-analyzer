package mk.ukim.finki.gitcontributionanalyzer.exception;
import mk.ukim.finki.gitcontributionanalyzer.enums.GeminiFailureCategory;
import mk.ukim.finki.gitcontributionanalyzer.enums.GeminiFailureReason;

public class GeminiException extends RuntimeException {

    private final GeminiFailureReason reason;

    public GeminiException(GeminiFailureReason reason) {
        super(reason.userMessage());
        this.reason = reason;
    }

    public GeminiException(GeminiFailureReason reason, Throwable cause) {
        super(reason.userMessage(), cause);
        this.reason = reason;
    }

    public GeminiFailureReason reason() {
        return reason;
    }

    public GeminiFailureCategory category() {
        return reason.category();
    }

    public String userMessage() {
        return reason.userMessage();
    }
}