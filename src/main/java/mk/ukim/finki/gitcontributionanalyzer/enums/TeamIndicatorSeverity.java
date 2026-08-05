package mk.ukim.finki.gitcontributionanalyzer.enums;

public enum TeamIndicatorSeverity {
    INFO("info"),
    WARNING("warning"),
    CRITICAL("critical");

    private final String cssClass;

    TeamIndicatorSeverity(String cssClass) {
        this.cssClass = cssClass;
    }

    public String cssClass() {
        return cssClass;
    }
}
