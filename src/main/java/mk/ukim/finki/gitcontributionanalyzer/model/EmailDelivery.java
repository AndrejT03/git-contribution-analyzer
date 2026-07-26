package mk.ukim.finki.gitcontributionanalyzer.model;

public record EmailDelivery(
        String status,
        String message
) {

    public static EmailDelivery pending() {
        return new EmailDelivery("PENDING", "The email report has not been sent yet.");
    }
}