package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.enums.*;
import mk.ukim.finki.gitcontributionanalyzer.model.RepositoryData;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.GeminiPromptBuilder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

class GeminiPromptBuilderTest {

    private final GeminiPromptBuilder promptBuilder = new GeminiPromptBuilder(new ObjectMapper());

    @Test
    void requestsExactUppercaseEnumNames() {
        RepositoryData repository = new RepositoryData(
                "https://github.com/team/project",
                "project",
                "main",
                List.of()
        );

        String prompt = promptBuilder.build("Team planning application", repository);

        assertThat(prompt)
                .contains("\"category\": \"" + enumNames(CommitCategory.values()) + "\"")
                .contains("\"contributionLevel\": \"" + enumNames(ContributionLevel.values()) + "\"")
                .contains("\"severity\": \"" + enumNames(TeamIndicatorSeverity.values()) + "\"");
    }

    private String enumNames(Enum<?>[] values) {
        return Stream.of(values)
                .map(Enum::name)
                .collect(Collectors.joining("|"));
    }
}
