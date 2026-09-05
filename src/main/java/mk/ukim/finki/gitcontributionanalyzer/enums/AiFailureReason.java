package mk.ukim.finki.gitcontributionanalyzer.enums;

public enum AiFailureReason {
    MISSING_API_KEY(
            AiFailureCategory.CONFIGURATION,
            "The Gemini API key is not configured."
    ),
    MISSING_MODEL(
            AiFailureCategory.CONFIGURATION,
            "The Gemini model is not configured."
    ),
    REQUEST_PREPARATION_FAILED(
            AiFailureCategory.REQUEST,
            "The repository data could not be prepared for Gemini."
    ),
    REQUEST_REJECTED(
            AiFailureCategory.REQUEST,
            "Gemini rejected the analysis request."
    ),
    CREDENTIALS_REJECTED(
            AiFailureCategory.AUTHENTICATION,
            "Gemini rejected the configured API credentials."
    ),
    MODEL_UNAVAILABLE(
            AiFailureCategory.CONFIGURATION,
            "The configured Gemini model is unavailable."
    ),
    RATE_LIMITED(
            AiFailureCategory.CAPACITY,
            "Gemini's request limit or quota was reached."
    ),
    NETWORK_ERROR(
            AiFailureCategory.CONNECTIVITY,
            "Gemini could not be reached because of a network connection problem."
    ),
    TIMEOUT(
            AiFailureCategory.CONNECTIVITY,
            "Gemini did not respond before the configured timeout."
    ),
    SERVICE_UNAVAILABLE(
            AiFailureCategory.PROVIDER,
            "Gemini is temporarily unavailable."
    ),
    BLOCKED_RESPONSE(
            AiFailureCategory.RESPONSE,
            "Gemini blocked the request and did not return an analysis."
    ),
    EMPTY_RESPONSE(
            AiFailureCategory.RESPONSE,
            "Gemini returned no analysis."
    ),
    INVALID_RESPONSE(
            AiFailureCategory.RESPONSE,
            "Gemini returned an incomplete or invalid analysis."
    );

    private final AiFailureCategory category;
    private final String userMessage;

    AiFailureReason(AiFailureCategory category, String userMessage) {
        this.category = category;
        this.userMessage = userMessage;
    }

    public AiFailureCategory category() {
        return category;
    }

    public String userMessage() {
        return userMessage;
    }
}