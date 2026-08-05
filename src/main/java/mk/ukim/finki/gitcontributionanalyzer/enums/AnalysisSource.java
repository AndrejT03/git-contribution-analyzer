package mk.ukim.finki.gitcontributionanalyzer.enums;

public enum AnalysisSource {
    GEMINI("Gemini AI"),
    LOCAL_FALLBACK("Local fallback");

    private final String displayName;

    AnalysisSource(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}