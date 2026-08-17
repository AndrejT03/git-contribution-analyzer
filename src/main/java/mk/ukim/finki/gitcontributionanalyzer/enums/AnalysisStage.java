package mk.ukim.finki.gitcontributionanalyzer.enums;

public enum AnalysisStage {
    QUEUED(
            0,
            "Queued",
            "Your analysis is queued and will begin shortly."
    ),
    STARTING(
            5,
            "Starting analysis",
            "Preparing your request and reserving analysis resources."
    ),
    READING_REPOSITORY(
            10,
            "Reading Git history",
            "Cloning history and collecting commits, authors, and diffs."
    ),
    ANALYZING_WITH_GEMINI(
            55,
            "Analyzing with Gemini",
            "Classifying commits and assessing their alignment with the project goal."
    ),
    LOCAL_FALLBACK(
            70,
            "Running local fallback",
            "Gemini is unavailable, so the deterministic local analyzer is continuing."
    ),
    PREPARING_REPORT(
            84,
            "Preparing report",
            "Composing charts, summaries, and highlights."
    ),
    SAVING_REPORT(
            89,
            "Saving report",
            "Making the completed report available in this browser."
    ),
    DELIVERING_EMAIL(
            95,
            "Delivering email copy",
            "Attempting to deliver the optional email copy."
    ),
    COMPLETED(
            100,
            "Completed",
            "Your contribution report is ready."
    );

    private final int progress;
    private final String label;
    private final String message;

    AnalysisStage(int progress, String label, String message) {
        this.progress = progress;
        this.label = label;
        this.message = message;
    }

    public int progress() {
        return progress;
    }

    public String label() {
        return label;
    }

    public String message() {
        return message;
    }
}