package mk.ukim.finki.gitcontributionanalyzer.enums;

public enum TeamIndicatorSeverity {
    INFO("Info", "info"),
    WARNING("Warning", "warning"),
    CRITICAL("Critical", "critical");

    private final String displayName;
    private final String cssClass;

    TeamIndicatorSeverity(String displayName, String cssClass) {
        this.displayName = displayName;
        this.cssClass = cssClass;
    }

    public String displayName() {
        return displayName;
    }
    public String cssClass() {
        return cssClass;
    }
}