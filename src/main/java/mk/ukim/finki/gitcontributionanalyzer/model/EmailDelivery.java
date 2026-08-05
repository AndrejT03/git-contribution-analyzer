package mk.ukim.finki.gitcontributionanalyzer.model;
import mk.ukim.finki.gitcontributionanalyzer.enums.EmailDeliveryStatus;

public record EmailDelivery(
        EmailDeliveryStatus status,
        String message
) {

    public static EmailDelivery pending() {
        return new EmailDelivery(EmailDeliveryStatus.PENDING, "The email report has not been sent yet.");
    }
}