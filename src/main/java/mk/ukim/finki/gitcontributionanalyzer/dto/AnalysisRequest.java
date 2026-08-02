package mk.ukim.finki.gitcontributionanalyzer.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AnalysisRequest {
    @NotBlank(message = "Enter the repository URL.")
    @Size(max = 300, message = "The URL is too long.")
    @Pattern(
            regexp = "^https://(github\\.com|gitlab\\.com)/[^/\\s]+/[^\\s?#]+(?:[?#][^\\s]*)?$",
            message = "Only HTTPS URLs from GitHub or GitLab are allowed."
    )
    private String repositoryUrl;

    @NotBlank(message = "Enter a short project description.")
    @Size(min = 20, max = 2000, message = "The project description must contain between 20 and 2000 characters.")
    private String projectDescription;

    @NotBlank(message = "Enter a valid email address.")
    @Email(message = "The email address is invalid.")
    private String email;

    public String getRepositoryUrl() {
        return repositoryUrl;
    }
    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
    public String getProjectDescription() {
        return projectDescription;
    }
    public void setProjectDescription(String projectDescription) {
        this.projectDescription = projectDescription;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
}