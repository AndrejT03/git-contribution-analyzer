package mk.ukim.finki.gitcontributionanalyzer.web;
import mk.ukim.finki.gitcontributionanalyzer.dto.*;
import mk.ukim.finki.gitcontributionanalyzer.enums.*;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisJob;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import mk.ukim.finki.gitcontributionanalyzer.model.EmailDelivery;
import mk.ukim.finki.gitcontributionanalyzer.service.ReportPdfService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

@Controller
@Profile("test")
public class DesignPreviewController {

    private static final UUID PREVIEW_JOB_ID = UUID.fromString("ca4e8875-6f49-46d3-b2f1-c6a660e612ee");
    private static final UUID PREVIEW_REPORT_ID = UUID.fromString("1632759b-970e-4f84-a044-dc3e2ecde90c");
    private static final OffsetDateTime PREVIEW_TIME = OffsetDateTime.parse("2026-08-09T12:00:00+02:00");

    private final ReportPdfService reportPdfService;

    public DesignPreviewController(ReportPdfService reportPdfService) {
        this.reportPdfService = reportPdfService;
    }

    @GetMapping("/__preview/progress")
    public String progress(
            @RequestParam(defaultValue = "QUEUED") AnalysisStage stage,
            @RequestParam(defaultValue = "GEMINI") AnalysisSource source,
            @RequestParam(defaultValue = "false") boolean chrome,
            Model model) {
        AnalysisJob job = previewJobAt(stage, source);

        model.addAttribute("job", job);
        model.addAttribute("jobStatus", AnalysisJobStatusDto.from(job));
        model.addAttribute("presentationJob", job);
        model.addAttribute("presentationJobStatus", AnalysisJobStatusDto.from(job));
        model.addAttribute("replayFromStart", false);
        model.addAttribute("analysisStages", AnalysisStage.values());
        model.addAttribute("previewMode", true);
        model.addAttribute("previewChrome", chrome);
        return "analysis-progress";
    }

    private AnalysisJob previewJobAt(AnalysisStage targetStage, AnalysisSource requestedSource) {
        AnalysisJob job = AnalysisJob.queued(PREVIEW_JOB_ID, "imageio/imageio-binaries", PREVIEW_TIME);
        if (targetStage == AnalysisStage.QUEUED) {
            return job;
        }

        job = job.advanceTo(AnalysisStage.STARTING, PREVIEW_TIME.plusSeconds(1));
        if (targetStage == AnalysisStage.STARTING) {
            return job;
        }

        job = job.advanceTo(AnalysisStage.READING_REPOSITORY, PREVIEW_TIME.plusSeconds(2));
        if (targetStage == AnalysisStage.READING_REPOSITORY) {
            return job;
        }

        job = job.advanceTo(AnalysisStage.ANALYZING_WITH_GEMINI, PREVIEW_TIME.plusSeconds(3));
        if (targetStage == AnalysisStage.ANALYZING_WITH_GEMINI) {
            return job;
        }
        AnalysisSource source = targetStage == AnalysisStage.LOCAL_FALLBACK
                ? AnalysisSource.LOCAL_FALLBACK
                : requestedSource;
        job = job.selectAnalysisSource(source, PREVIEW_TIME.plusSeconds(4));
        if (source == AnalysisSource.LOCAL_FALLBACK) {
            job = job.advanceTo(AnalysisStage.LOCAL_FALLBACK, PREVIEW_TIME.plusSeconds(5));
            if (targetStage == AnalysisStage.LOCAL_FALLBACK) {
                return job;
            }
        }

        job = job.advanceTo(AnalysisStage.PREPARING_REPORT, PREVIEW_TIME.plusSeconds(6));
        if (targetStage == AnalysisStage.PREPARING_REPORT) {
            return job;
        }

        job = job.advanceTo(AnalysisStage.SAVING_REPORT, PREVIEW_TIME.plusSeconds(7));
        if (targetStage == AnalysisStage.SAVING_REPORT) {
            return job;
        }

        job = job.advanceTo(AnalysisStage.DELIVERING_EMAIL, PREVIEW_TIME.plusSeconds(8));
        if (targetStage == AnalysisStage.DELIVERING_EMAIL) {
            return job;
        }
        return job.complete(PREVIEW_REPORT_ID, source, PREVIEW_TIME.plusSeconds(9));
    }

    @GetMapping("/__preview/report")
    public String report(Model model) {
        model.addAttribute("report", previewReport());
        model.addAttribute("newReport", true);
        model.addAttribute("commitCategories", CommitCategory.values());
        model.addAttribute("previewMode", true);
        model.addAttribute("reportPdfUrl", "/__preview/report/pdf");
        model.addAttribute("reportPdfDownloadUrl", "/__preview/report/pdf?download=true");
        return "report";
    }

    @GetMapping("/__preview/report-scale")
    public String reportScale(
            @RequestParam(defaultValue = "false") boolean single,
            Model model) {
        model.addAttribute("report", single ? singleContributorReport() : scalingReport());
        model.addAttribute("newReport", false);
        model.addAttribute("commitCategories", CommitCategory.values());
        model.addAttribute("previewMode", false);
        model.addAttribute("reportPdfUrl", "/__preview/report-scale/pdf");
        model.addAttribute("reportPdfDownloadUrl", "/__preview/report-scale/pdf?download=true");
        return "report";
    }

    @GetMapping("/__preview/report/pdf")
    @ResponseBody
    public ResponseEntity<byte[]> reportPdf(
            @RequestParam(defaultValue = "false") boolean download) {
        return previewPdfResponse(previewReport(), "orbital-labs-flightdeck-contribution-report.pdf", download);
    }

    @GetMapping("/__preview/report-scale/pdf")
    @ResponseBody
    public ResponseEntity<byte[]> reportScalePdf(
            @RequestParam(defaultValue = "false") boolean download) {
        return previewPdfResponse(scalingReport(), "report-scale-contribution-report.pdf", download);
    }

    @GetMapping("/__preview/error")
    public String error(Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("title", "Report not found");
        model.addAttribute("message", "The requested analysis is no longer available.");
        model.addAttribute("errorHeading", "We couldn't find that analysis");
        model.addAttribute(
                "errorDescription",
                "The analysis or report you're looking for doesn't exist or is no longer available. "
                        + "Reports are kept only for the current session and are not stored."
        );
        model.addAttribute("previewMode", true);
        return "error-page";
    }

    private ResponseEntity<byte[]> previewPdfResponse(
            AnalysisReport report,
            String filename,
            boolean download) {
        byte[] pdf = reportPdfService.createPdf(report);
        ContentDisposition disposition = (download
                ? ContentDisposition.attachment()
                : ContentDisposition.inline())
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(pdf);
    }

    private AnalysisReport previewReport() {
        return new AnalysisReport(
                PREVIEW_REPORT_ID,
                "https://github.com/orbital-labs/flightdeck",
                "orbital-labs/flightdeck",
                "main",
                "Ship a resilient telemetry pipeline and control dashboard so a single operator can safely "
                        + "supervise up to 24 concurrent drones, with graceful degradation when connectivity drops.",
                "team@orbital-labs.dev",
                AnalysisSource.GEMINI,
                "Gemini 2.5 Pro",
                "Gemini completed the contribution analysis.",
                486,
                PREVIEW_TIME,
                new ContributionAnalysis(
                        "A real-time operations console for coordinating autonomous drone fleets, built with a "
                                + "TypeScript service layer and a React control surface.",
                        "Contributions align strongly with the stated goal. The bulk of functional work targets "
                                + "the telemetry pipeline and failover paths, and testing coverage clusters around "
                                + "connection-loss scenarios. Documentation of operator procedures lags slightly "
                                + "behind implementation.",
                        List.of(amara(), dmitri(), priya(), lea()),
                        List.of(
                                new TeamIndicator(
                                        "core-ownership",
                                        TeamIndicatorSeverity.INFO,
                                        "Clear ownership of core systems",
                                        "Telemetry and control-surface responsibilities are visible and well scoped."
                                ),
                                new TeamIndicator(
                                        "concentration",
                                        TeamIndicatorSeverity.WARNING,
                                        "Knowledge is concentrated",
                                        "The two leading contributors own most runtime-critical paths."
                                )
                        ),
                        "Work is distributed across four contributors with two clear leads on the core pipeline "
                                + "and control surface. Configuration and documentation are covered but concentrated, "
                                + "and one contributor's Git-visible share is low. Overall the team's output aligns "
                                + "well with the resilience-focused goal.",
                        "Commits from the default branch were categorized and weighted by scope and importance. "
                                + "Percentages are derived from weighted commit contribution — not lines changed "
                                + "alone — and the same rubric is applied across contributors."
                ),
                new EmailDelivery(
                        EmailDeliveryStatus.FAILED,
                        "We could not reach the address you provided, so the secondary email copy did not send. "
                                + "This does not affect your report — everything is available here in the browser."
                )
        );
    }

    private AnalysisReport scalingReport() {
        AnalysisReport reference = previewReport();
        ContributorAnalysis lead = withPercentage(amara(), 65);
        ContributorAnalysis partner = withPercentage(dmitri(), 35);
        ContributionAnalysis analysis = new ContributionAnalysis(
                reference.analysis().projectSummary(),
                reference.analysis().goalAlignment(),
                List.of(lead, partner),
                reference.analysis().teamIndicators(),
                reference.analysis().conclusion(),
                reference.analysis().methodology()
        );
        return new AnalysisReport(
                UUID.fromString("69243042-43f5-45ca-8f1c-997780c07870"),
                reference.repositoryUrl(),
                reference.repositoryName(),
                reference.defaultBranch(),
                reference.projectDescription(),
                reference.requestedEmail(),
                reference.analysisSource(),
                reference.analysisModel(),
                reference.analysisNotice(),
                200,
                reference.generatedAt(),
                analysis,
                reference.emailDelivery()
        );
    }

    private AnalysisReport singleContributorReport() {
        AnalysisReport reference = previewReport();
        ContributorAnalysis contributor = withPercentage(amara(), 100);
        ContributionAnalysis analysis = new ContributionAnalysis(
                reference.analysis().projectSummary(),
                reference.analysis().goalAlignment(),
                List.of(contributor),
                List.of(),
                "One contributor owns the full Git-visible contribution represented in this report.",
                reference.analysis().methodology()
        );
        return new AnalysisReport(
                UUID.fromString("c480458e-85ec-4e31-bbed-8bf5fd1bc028"),
                reference.repositoryUrl(),
                reference.repositoryName(),
                reference.defaultBranch(),
                reference.projectDescription(),
                reference.requestedEmail(),
                reference.analysisSource(),
                reference.analysisModel(),
                reference.analysisNotice(),
                100,
                reference.generatedAt(),
                analysis,
                reference.emailDelivery()
        );
    }

    private ContributorAnalysis withPercentage(ContributorAnalysis contributor, int percentage) {
        return new ContributorAnalysis(
                contributor.name(),
                contributor.email(),
                percentage,
                ContributionLevel.fromPercentage(percentage),
                contributor.summary(),
                contributor.mainWork(),
                contributor.categorySummary(),
                contributor.commitAnalyses(),
                contributor.riskFlags()
        );
    }

    private ContributorAnalysis amara() {
        List<CategorySummary> categories = List.of(
                category(CommitCategory.FUNCTIONAL, 61, "Core telemetry and operator-safety features."),
                category(CommitCategory.TESTING, 18, "Connection-loss behavior is covered."),
                category(CommitCategory.REFACTORING, 12, "Socket state transitions were simplified."),
                category(CommitCategory.BUG_FIX, 9, "Targeted runtime corrections.")
        );
        List<CommitAnalysis> featured = List.of(
                commit("3c9de44", "Enforce max concurrent drones per operator", CommitCategory.FUNCTIONAL,
                        4, "Safety limit tied directly to the project goal."),
                commit("a8f291c", "Add backpressure to telemetry ingestion", CommitCategory.FUNCTIONAL,
                        5, "Protects the console when telemetry volume spikes."),
                commit("8bc41f9", "Cover reconnect failover transitions", CommitCategory.TESTING,
                        4, "Verifies the most important connection-loss scenarios."),
                commit("c01be73", "Extract resilient socket state machine", CommitCategory.REFACTORING,
                        4, "Makes failover behavior explicit and easier to maintain.")
        );
        return new ContributorAnalysis(
                "Amara Okafor",
                "amara.okafor@orbital-labs.dev",
                38,
                ContributionLevel.HIGH,
                "Owned the telemetry ingestion pipeline and the failover state machine that keeps the console "
                        + "usable during connection loss.",
                List.of("Telemetry pipeline", "WebSocket reconnection", "failover state machine"),
                categories,
                completeCommits("e", featured, categories),
                List.of("Consistent, well-tested functional work concentrated on the core pipeline.")
        );
    }

    private ContributorAnalysis dmitri() {
        List<CategorySummary> categories = List.of(
                category(CommitCategory.FUNCTIONAL, 54, "Primary control-surface functionality."),
                category(CommitCategory.REFACTORING, 20, "Reusable map state and selection logic."),
                category(CommitCategory.BUG_FIX, 16, "Corrected stale drone markers."),
                category(CommitCategory.FORMATTING, 10, "Small formatting-only changes.")
        );
        List<CommitAnalysis> featured = List.of(
                commit("2b77d81", "Build multi-drone selection controls", CommitCategory.FUNCTIONAL,
                        5, "Delivers a central operator workflow."),
                commit("7d01ac4", "Render live fleet positions on the map", CommitCategory.FUNCTIONAL,
                        5, "Creates the dashboard's main operational view."),
                commit("cb41f80", "Extract reusable map viewport state", CommitCategory.REFACTORING,
                        3, "Reduces coupling between controls and map rendering."),
                commit("5a6de92", "Fix stale markers after reconnect", CommitCategory.BUG_FIX,
                        4, "Prevents operators from seeing outdated drone positions.")
        );
        return new ContributorAnalysis(
                "Dmitri Volkov",
                "dmitri.volkov@orbital-labs.dev",
                27,
                ContributionLevel.HIGH,
                "Built the React control surface, map rendering, and the multi-drone selection model that "
                        + "operators use most.",
                List.of("Control dashboard UI", "map rendering", "drone selection model"),
                categories,
                completeCommits("f", featured, categories),
                List.of("Strong UI ownership; formatting-only commits are a small share of total work.")
        );
    }

    private ContributorAnalysis priya() {
        List<CategorySummary> categories = List.of(
                category(CommitCategory.CONFIGURATION, 82, "Reproducible deployment configuration."),
                category(CommitCategory.FUNCTIONAL, 52, "Operator safety constraints."),
                category(CommitCategory.TESTING, 30, "Deployment safety verification."),
                category(CommitCategory.DOCUMENTATION, 22, "Environment setup guidance.")
        );
        List<CommitAnalysis> featured = List.of(
                commit("d4b6f10", "Enforce max concurrent drones per operator", CommitCategory.FUNCTIONAL,
                        4, "Safety limit tied directly to the project goal."),
                commit("f012ab6", "Add staging and production deploy pipelines", CommitCategory.CONFIGURATION,
                        3, "Establishes a reproducible deployment path."),
                commit("9aec7c2", "Document environment variables", CommitCategory.DOCUMENTATION,
                        2, "Clarifies required configuration for new contributors.")
        );
        return new ContributorAnalysis(
                "Priya Nair",
                "priya.nair@orbital-labs.dev",
                21,
                ContributionLevel.MEDIUM,
                "Focused on configuration, deployment, and the safety limits that cap concurrent drones per operator.",
                List.of("Deployment config", "environment safety limits", "CI pipeline"),
                categories,
                completeCommits("1", featured, categories),
                List.of("Much of the impact is configuration; functional depth is moderate. Worth pairing on core features.")
        );
    }

    private ContributorAnalysis lea() {
        List<CategorySummary> categories = List.of(
                category(CommitCategory.DOCUMENTATION, 58, "Operator and contributor guidance."),
                category(CommitCategory.BUG_FIX, 24, "Targeted late-cycle correction."),
                category(CommitCategory.FORMATTING, 12, "Formatting and editorial cleanup."),
                category(CommitCategory.OTHER, 6, "Other supporting changes.")
        );
        List<CommitAnalysis> featured = List.of(
                commit("4d8ba19", "Write operator incident checklist", CommitCategory.DOCUMENTATION,
                        3, "Gives operators a concise recovery procedure."),
                commit("67f20ce", "Add contributor onboarding guide", CommitCategory.DOCUMENTATION,
                        2, "Reduces setup friction for new contributors."),
                commit("ab801d5", "Fix units in battery warning copy", CommitCategory.BUG_FIX,
                        2, "Corrects a small but user-visible dashboard issue.")
        );
        return new ContributorAnalysis(
                "Léa Moreau",
                "lea.moreau@orbital-labs.dev",
                14,
                ContributionLevel.LOW,
                "Contributed documentation, onboarding guides, and a handful of targeted bug fixes late in the cycle.",
                List.of("Operator handbook", "onboarding docs", "miscellaneous bug fixes"),
                categories,
                completeCommits("2", featured, categories),
                List.of("Low Git-visible contribution. This may under-represent design, review, or mentoring done off-repo.")
        );
    }

    private CategorySummary category(CommitCategory category, int count, String explanation) {
        return new CategorySummary(category, count, explanation);
    }

    private List<CommitAnalysis> completeCommits(
            String hashPrefix,
            List<CommitAnalysis> featured,
            List<CategorySummary> categories) {
        List<CommitAnalysis> commits = new ArrayList<>(featured);
        Map<CommitCategory, Integer> existingCounts = new EnumMap<>(CommitCategory.class);
        featured.forEach(commit -> existingCounts.merge(commit.category(), 1, Integer::sum));

        int sequence = 1;
        for (CategorySummary category : categories) {
            int missing = category.commitCount() - existingCounts.getOrDefault(category.category(), 0);
            for (int index = 0; index < missing; index++) {
                commits.add(commit(
                        hashPrefix + "%06x".formatted(sequence++),
                        "Supporting " + category.category().displayName().toLowerCase() + " change",
                        category.category(),
                        2,
                        category.explanation()
                ));
            }
        }
        return List.copyOf(commits);
    }

    private CommitAnalysis commit(
            String hash,
            String message,
            CommitCategory category,
            int importance,
            String explanation) {
        return new CommitAnalysis(hash, message, category, importance, explanation);
    }
}