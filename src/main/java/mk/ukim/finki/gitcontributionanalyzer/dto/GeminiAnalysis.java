package mk.ukim.finki.gitcontributionanalyzer.dto;

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