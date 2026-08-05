package mk.ukim.finki.gitcontributionanalyzer.dto;
import java.util.ArrayList;
import java.util.List;

public class ContributorWork {
    public final String name;
    public final String email;
    public final List<CommitAnalysis> commitAnalyses = new ArrayList<>();

    public ContributorWork(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public int score() {
        return commitAnalyses.stream().mapToInt(CommitAnalysis::importance).sum();
    }
}