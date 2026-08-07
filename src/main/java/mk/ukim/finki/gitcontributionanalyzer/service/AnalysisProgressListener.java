package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStage;

@FunctionalInterface
public interface AnalysisProgressListener {
    void onStage(AnalysisStage stage);
    static AnalysisProgressListener none() {
        return ignored -> { };
    }
}