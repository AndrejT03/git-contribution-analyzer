package mk.ukim.finki.gitcontributionanalyzer.enums;

public enum CommitCategory {
    FUNCTIONAL("Functional", "functional"),
    BUG_FIX("Bug Fix", "bug-fix"),
    REFACTORING("Refactoring", "refactoring"),
    DOCUMENTATION("Documentation", "documentation"),
    FORMATTING("Formatting", "formatting"),
    TESTING("Testing", "testing"),
    CONFIGURATION("Configuration", "configuration"),
    OTHER("Other", "other");

    private final String displayName;
    private final String cssClass;

    CommitCategory(String displayName, String cssClass) {
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