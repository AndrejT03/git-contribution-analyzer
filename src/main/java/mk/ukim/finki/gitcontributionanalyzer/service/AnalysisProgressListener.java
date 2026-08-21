package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisSource;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStage;

@FunctionalInterface
public interface AnalysisProgressListener {
    void onStage(AnalysisStage stage);

    default void onAnalysisSource(AnalysisSource source) {}
}