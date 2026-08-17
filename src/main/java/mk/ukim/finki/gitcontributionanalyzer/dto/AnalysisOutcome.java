package mk.ukim.finki.gitcontributionanalyzer.dto;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisSource;

public record AnalysisOutcome(
        ContributionAnalysis analysis,
        AnalysisSource source,
        String model,
        String notice
) {
}