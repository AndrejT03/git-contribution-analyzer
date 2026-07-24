package mk.ukim.finki.gitcontributionanalyzer.model;
import java.util.List;

public record GeminiAnalysis(
        String projectSummary,
        String goalAlignment,
        List<ContributorAnalysis> contributors,
        List<TeamIndicator> teamIndicators,
        String conclusion,
        String methodology
) {
}