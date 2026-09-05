package mk.ukim.finki.gitcontributionanalyzer.dto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class AnalysisRequestDtoTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptsAndNormalizesARequestSelectedProviderAndKey() {
        AnalysisRequest request = request(
                "  request-scoped-secret  ",
                "  anthropic::claude-sonnet-5  "
        );

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.aiKey()).isEqualTo("request-scoped-secret");
        assertThat(request.aiModel()).isEqualTo("anthropic::claude-sonnet-5");
    }

    @Test
    void rejectsAProviderModelCombinationOutsideTheAllowlist() {
        Set<ConstraintViolation<AnalysisRequest>> violations = validator.validate(
                request("request-scoped-secret", "openai::claude-sonnet-5")
        );

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("aiModel");
    }

    @Test
    void rejectsLineBreaksInApiKeys() {
        Set<ConstraintViolation<AnalysisRequest>> violations = validator.validate(
                request("secret\r\nInjected-Header: value", "openai::gpt-5.6-terra")
        );

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("aiKey");
    }

    @Test
    void redactsTheApiKeyAndProjectDescriptionFromDiagnosticText() {
        AnalysisRequest request = request(
                "never-log-this-secret",
                "openai::gpt-5.6-terra"
        );

        assertThat(request.toString())
                .contains("aiKey=<redacted>")
                .contains("projectDescription=<redacted>")
                .contains("aiModel=openai::gpt-5.6-terra")
                .doesNotContain("never-log-this-secret")
                .doesNotContain("A sufficiently long private project description.");
    }

    private AnalysisRequest request(String key, String model) {
        return new AnalysisRequest(
                "https://github.com/team/project",
                "A sufficiently long private project description.",
                "mentor@example.com",
                key,
                model
        );
    }
}