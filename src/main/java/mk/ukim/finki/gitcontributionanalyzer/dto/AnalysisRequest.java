package mk.ukim.finki.gitcontributionanalyzer.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mk.ukim.finki.gitcontributionanalyzer.validation.SupportedAiModel;

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
        String email,

        @NotBlank(message = "Enter an AI API key.")
        @Size(max = 1024, message = "The AI API key is too long.")
        @Pattern(regexp = "^[^\\r\\n]+$", message = "The AI API key contains invalid characters.")
        String aiKey,

        @NotBlank(message = "Choose an AI provider and model.")
        @Size(max = 250, message = "The AI model selection is too long.")
        @SupportedAiModel
        String aiModel
) {

    public AnalysisRequest {
        aiKey = aiKey == null ? null : aiKey.strip();
        aiModel = aiModel == null ? null : aiModel.strip();
    }

    public static AnalysisRequest empty() {
        return new AnalysisRequest("", "", "", "", "");
    }

    @Override
    public String toString() {
        return "AnalysisRequestDto[repositoryUrl=%s, projectDescription=<redacted>, email=%s, "
                .formatted(repositoryUrl, email)
                + "aiKey=<redacted>, aiModel=%s]".formatted(aiModel);
    }
}