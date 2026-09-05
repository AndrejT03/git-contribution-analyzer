package mk.ukim.finki.gitcontributionanalyzer.validation;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = SupportedAiModelValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SupportedAiModel {

    String message() default "Choose a supported AI provider and model.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}