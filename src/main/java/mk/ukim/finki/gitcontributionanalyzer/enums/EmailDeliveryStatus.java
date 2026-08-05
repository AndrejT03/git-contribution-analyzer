package mk.ukim.finki.gitcontributionanalyzer.enums;

public enum EmailDeliveryStatus {
    PENDING("pending"),
    DISABLED("disabled"),
    SENT("sent"),
    FAILED("failed");

    private final String cssClass;

    EmailDeliveryStatus(String cssClass) {
        this.cssClass = cssClass;
    }

    public String cssClass() {
        return cssClass;
    }
}