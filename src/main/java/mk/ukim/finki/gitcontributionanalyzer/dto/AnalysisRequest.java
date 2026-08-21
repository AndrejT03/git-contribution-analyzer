package mk.ukim.finki.gitcontributionanalyzer.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AnalysisRequest (
        @NotBlank(message = "Enter the repository URL.")
        @Size(max = 300, message = "The URL is too long.")
        @Pattern(
                regexp = "^https://(github\\.com|gitlab\\.com)/[^/\\s]+/[^\\s?#]+.*$",
                message = "Only HTTPS URLs from GitHub or GitLab are allowed."
        )
        String repositoryUrl,

        @NotBlank(message = "Enter a short project description.")
        @Size(min = 20, max = 2000, message = "The description must contain between 20 and 2000 characters.")
        String projectDescription,

        @NotBlank(message = "Enter an email address.")
        @Email(message = "The email address is invalid.")
        String email
) {

    public static AnalysisRequest empty() {
        return new AnalysisRequest("", "", "");
    }
}