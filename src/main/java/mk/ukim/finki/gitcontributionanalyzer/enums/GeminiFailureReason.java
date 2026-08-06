package mk.ukim.finki.gitcontributionanalyzer.enums;

public enum GeminiFailureReason {
    MISSING_API_KEY(
            GeminiFailureCategory.CONFIGURATION,
            "The Gemini API key is not configured."
    ),
    MISSING_MODEL(
            GeminiFailureCategory.CONFIGURATION,
            "The Gemini model is not configured."
    ),
    REQUEST_PREPARATION_FAILED(
            GeminiFailureCategory.REQUEST,
            "The repository data could not be prepared for Gemini."
    ),
    REQUEST_REJECTED(
            GeminiFailureCategory.REQUEST,
            "Gemini rejected the analysis request."
    ),
    CREDENTIALS_REJECTED(
            GeminiFailureCategory.AUTHENTICATION,
            "Gemini rejected the configured API credentials."
    ),
    MODEL_UNAVAILABLE(
            GeminiFailureCategory.CONFIGURATION,
            "The configured Gemini model is unavailable."
    ),
    RATE_LIMITED(
            GeminiFailureCategory.CAPACITY,
            "Gemini's request limit or quota was reached."
    ),
    NETWORK_ERROR(
            GeminiFailureCategory.CONNECTIVITY,
            "Gemini could not be reached because of a network connection problem."
    ),
    TIMEOUT(
            GeminiFailureCategory.CONNECTIVITY,
            "Gemini did not respond before the configured timeout."
    ),
    SERVICE_UNAVAILABLE(
            GeminiFailureCategory.PROVIDER,
            "Gemini is temporarily unavailable."
    ),
    BLOCKED_RESPONSE(
            GeminiFailureCategory.RESPONSE,
            "Gemini blocked the request and did not return an analysis."
    ),
    EMPTY_RESPONSE(
            GeminiFailureCategory.RESPONSE,
            "Gemini returned no analysis."
    ),
    INVALID_RESPONSE(
            GeminiFailureCategory.RESPONSE,
            "Gemini returned an incomplete or invalid analysis."
    ),
    CONTRIBUTION_LEVEL_MISMATCH(
            GeminiFailureCategory.RESPONSE,
            "Gemini returned a contribution level that does not match its percentage."
    );

    private final GeminiFailureCategory category;
    private final String userMessage;

    GeminiFailureReason(GeminiFailureCategory category, String userMessage) {
        this.category = category;
        this.userMessage = userMessage;
    }

    public GeminiFailureCategory category() {
        return category;
    }

    public String userMessage() {
        return userMessage;
    }
}