package mk.ukim.finki.gitcontributionanalyzer.dto;

import java.util.List;

public record ContributionAnalysis(
        String projectSummary,
        String goalAlignment,
        List<ContributorAnalysis> contributors,
        List<TeamIndicator> teamIndicators,
        String conclusion,
        String methodology
) {
}