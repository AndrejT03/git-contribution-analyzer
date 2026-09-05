package mk.ukim.finki.gitcontributionanalyzer.validation;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import mk.ukim.finki.gitcontributionanalyzer.model.AiProviderSelection;

public class SupportedAiModelValidator implements ConstraintValidator<SupportedAiModel, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || AiProviderSelection.isSupported(value);
    }
}