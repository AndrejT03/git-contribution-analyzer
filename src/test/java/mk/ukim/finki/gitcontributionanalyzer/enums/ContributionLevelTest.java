package mk.ukim.finki.gitcontributionanalyzer.enums;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContributionLevelTest {

    @Test
    void mapsPercentageBoundariesToContributionLevels() {
        assertThat(ContributionLevel.fromPercentage(0)).isEqualTo(ContributionLevel.LOW);
        assertThat(ContributionLevel.fromPercentage(14)).isEqualTo(ContributionLevel.LOW);
        assertThat(ContributionLevel.fromPercentage(19)).isEqualTo(ContributionLevel.LOW);
        assertThat(ContributionLevel.fromPercentage(24)).isEqualTo(ContributionLevel.MEDIUM);
        assertThat(ContributionLevel.fromPercentage(25)).isEqualTo(ContributionLevel.MEDIUM);
        assertThat(ContributionLevel.fromPercentage(100)).isEqualTo(ContributionLevel.HIGH);
    }

    @Test
    void rejectsPercentageOutsideTheValidRange() {
        assertThatThrownBy(() -> ContributionLevel.fromPercentage(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ContributionLevel.fromPercentage(101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}