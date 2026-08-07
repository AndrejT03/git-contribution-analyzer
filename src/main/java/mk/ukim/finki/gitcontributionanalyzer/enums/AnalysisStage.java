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
            "The analysis worker is preparing your request."
    ),
    READING_REPOSITORY(
            10,
            "Reading Git history",
            "The repository is being downloaded and its commit history is being read."
    ),
    ANALYZING_WITH_GEMINI(
            55,
            "Analyzing with Gemini",
            "Gemini is evaluating the commits against the supplied project goal."
    ),
    LOCAL_FALLBACK(
            70,
            "Running local fallback",
            "Gemini was unavailable, so the built-in local analyzer is evaluating the commits."
    ),
    PREPARING_REPORT(
            82,
            "Preparing report",
            "The contribution findings are being organized into the final report."
    ),
    SAVING_REPORT(
            88,
            "Saving report",
            "The completed report is being made available in this browser."
    ),
    DELIVERING_EMAIL(
            94,
            "Delivering email copy",
            "The optional email delivery step is being completed."
    ),
    COMPLETED(
            100,
            "Analysis completed",
            "The complete report is ready."
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