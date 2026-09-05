package mk.ukim.finki.gitcontributionanalyzer.exception;
import mk.ukim.finki.gitcontributionanalyzer.enums.AiFailureCategory;
import mk.ukim.finki.gitcontributionanalyzer.enums.AiFailureReason;
import mk.ukim.finki.gitcontributionanalyzer.enums.AiProvider;

public class AiProviderException extends RuntimeException {
    private final AiFailureReason reason;
    private final AiProvider provider;

    public AiProviderException(AiFailureReason reason) {
        this(reason, null, null);
    }

    public AiProviderException(AiFailureReason reason, Throwable cause) {
        this(reason, null, cause);
    }

    public AiProviderException(AiFailureReason reason, AiProvider provider) {
        this(reason, provider, null);
    }

    public AiProviderException(
            AiFailureReason reason,
            AiProvider provider,
            Throwable cause
    ) {
        super(reason.userMessage(), cause);
        this.reason = reason;
        this.provider = provider;
    }

    public AiFailureReason reason() {
        return reason;
    }

    public AiFailureCategory category() {
        return reason.category();
    }

    public AiProvider provider() {
        return provider;
    }

    public String userMessage() {
        return reason.userMessage();
    }
}