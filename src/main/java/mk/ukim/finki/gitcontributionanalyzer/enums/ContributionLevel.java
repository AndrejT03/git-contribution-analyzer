package mk.ukim.finki.gitcontributionanalyzer.enums;

public enum ContributionLevel {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low");

    private final String displayName;

    ContributionLevel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static ContributionLevel fromPercentage(int percentage) {
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("Contribution percentage must be between 0 and 100.");
        }
        if (percentage >= 40) {
            return HIGH;
        }
        if (percentage >= 20) {
            return MEDIUM;
        }
        return LOW;
    }
}