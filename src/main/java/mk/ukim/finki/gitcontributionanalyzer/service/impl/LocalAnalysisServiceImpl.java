package mk.ukim.finki.gitcontributionanalyzer.service.impl;
import mk.ukim.finki.gitcontributionanalyzer.dto.*;
import mk.ukim.finki.gitcontributionanalyzer.enums.CommitCategory;
import mk.ukim.finki.gitcontributionanalyzer.enums.ContributionLevel;
import mk.ukim.finki.gitcontributionanalyzer.enums.TeamIndicatorSeverity;
import mk.ukim.finki.gitcontributionanalyzer.model.GitCommit;
import mk.ukim.finki.gitcontributionanalyzer.model.RepositoryData;
import mk.ukim.finki.gitcontributionanalyzer.service.LocalAnalysisService;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class LocalAnalysisServiceImpl implements LocalAnalysisService {

    private final LocalCommitClassifier classifier;

    public LocalAnalysisServiceImpl(LocalCommitClassifier classifier) {
        this.classifier = classifier;
    }

    @Override
    public ContributionAnalysis analyze(String projectDescription, RepositoryData repositoryData) {
        if (repositoryData.commits().isEmpty()) {
            throw new IllegalArgumentException("Local analysis requires at least one commit.");
        }

        List<ContributorWork> workByContributor = groupCommits(
                projectDescription,
                repositoryData.commits()
        );
        int[] percentages = calculatePercentages(workByContributor);

        List<ContributorAnalysis> contributors = new ArrayList<>();
        for (int index = 0; index < workByContributor.size(); index++) {
            contributors.add(toContributorAnalysis(workByContributor.get(index), percentages[index]));
        }

        return new ContributionAnalysis(
                "Local analysis of " + repositoryData.name() + " with "
                        + repositoryData.commits().size() + " commits.",
                "Goal alignment is estimated from project keywords found in commit messages and file paths.",
                contributors,
                createTeamIndicators(contributors),
                "The local estimate summarizes visible Git activity for "
                        + contributors.size() + " contributor(s).",
                "Local analysis used rule-based categories, changed-line ranges, project-keyword matching, "
                        + "and score-based percentages. It does not provide AI semantic interpretation."
        );
    }

    private List<ContributorWork> groupCommits(String projectDescription, List<GitCommit> commits) {
        Map<String, ContributorWork> grouped = new LinkedHashMap<>();

        for (GitCommit commit : commits) {
            String email = safeValue(commit.authorEmail(), "unknown@example.com");
            String name = safeValue(commit.authorName(), "Unknown contributor");
            String identity = email.toLowerCase(Locale.ROOT);
            ContributorWork work = grouped.computeIfAbsent(
                    identity,
                    ignored -> new ContributorWork(name, email)
            );
            work.commitAnalyses.add(classifier.analyze(commit, projectDescription));
        }

        return new ArrayList<>(grouped.values());
    }

    private int[] calculatePercentages(List<ContributorWork> contributors) {
        if (contributors.isEmpty()) {
            return new int[0];
        }

        int totalScore = contributors.stream().mapToInt(ContributorWork::score).sum();
        int[] percentages = new int[contributors.size()];
        int[] remainders = new int[contributors.size()];
        int assignedPercentage = 0;

        for (int index = 0; index < contributors.size(); index++) {
            int scaledScore = contributors.get(index).score() * 100;
            percentages[index] = scaledScore / totalScore;
            remainders[index] = scaledScore % totalScore;
            assignedPercentage += percentages[index];
        }

        List<Integer> remainderOrder = new ArrayList<>();
        for (int index = 0; index < contributors.size(); index++) {
            remainderOrder.add(index);
        }
        remainderOrder.sort(Comparator
                .comparingInt((Integer index) -> remainders[index])
                .reversed()
                .thenComparing(index -> contributors.get(index).email));

        int remaining = 100 - assignedPercentage;
        for (int index = 0; index < remaining; index++) {
            percentages[remainderOrder.get(index)]++;
        }
        return percentages;
    }

    private ContributorAnalysis toContributorAnalysis(ContributorWork work, int percentage) {
        Map<CommitCategory, Integer> categoryCounts = new LinkedHashMap<>();
        for (CommitAnalysis commit : work.commitAnalyses) {
            categoryCounts.merge(commit.category(), 1, Integer::sum);
        }

        List<CategorySummary> categorySummary = categoryCounts.entrySet().stream()
                .map(entry -> new CategorySummary(
                        entry.getKey(),
                        entry.getValue(),
                        entry.getValue() + " local commit(s) classified as " + entry.getKey() + "."
                ))
                .toList();

        List<String> mainWork = categoryCounts.entrySet().stream()
                .sorted(Map.Entry.<CommitCategory, Integer>comparingByValue().reversed())
                .limit(3)
                .map(entry -> entry.getValue() + " "
                        + entry.getKey().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                        + " commit(s)")
                .toList();

        List<String> riskFlags = new ArrayList<>();
        if (percentage < 10) {
            riskFlags.add("Contribution share is below 10% in the local estimate.");
        }
        boolean onlyDocumentationOrFormatting = work.commitAnalyses.stream()
                .allMatch(commit -> commit.category() == CommitCategory.DOCUMENTATION
                        || commit.category() == CommitCategory.FORMATTING);
        if (onlyDocumentationOrFormatting) {
            riskFlags.add("All detected work is documentation or formatting.");
        }

        return new ContributorAnalysis(
                work.name,
                work.email,
                percentage,
                ContributionLevel.fromPercentage(percentage),
                work.name + " authored " + work.commitAnalyses.size()
                        + " analyzed commit(s) with a local score of " + work.score() + ".",
                mainWork,
                categorySummary,
                List.copyOf(work.commitAnalyses),
                riskFlags
        );
    }

    private List<TeamIndicator> createTeamIndicators(List<ContributorAnalysis> contributors) {
        if (contributors.size() == 1) {
            return List.of(new TeamIndicator(
                    "TEAM_SIZE",
                    TeamIndicatorSeverity.INFO,
                    "Single contributor",
                    "Only one contributor identity was found in the analyzed commits."
            ));
        }

        int highest = contributors.stream()
                .mapToInt(ContributorAnalysis::contributionPercentage)
                .max()
                .orElse(0);
        int lowest = contributors.stream()
                .mapToInt(ContributorAnalysis::contributionPercentage)
                .min()
                .orElse(0);

        if (highest >= 70 || highest - lowest >= 40) {
            return List.of(new TeamIndicator(
                    "IMBALANCE",
                    TeamIndicatorSeverity.WARNING,
                    "Possible contribution imbalance",
                    "The local estimate found a large difference between contributor shares."
            ));
        }
        return List.of(new TeamIndicator(
                "BALANCE",
                TeamIndicatorSeverity.INFO,
                "No strong imbalance detected",
                "Contributor shares are within the local warning thresholds."
        ));
    }

    private String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}