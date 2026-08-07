package mk.ukim.finki.gitcontributionanalyzer.web;
import mk.ukim.finki.gitcontributionanalyzer.dto.*;
import mk.ukim.finki.gitcontributionanalyzer.enums.*;
import mk.ukim.finki.gitcontributionanalyzer.exception.ReportNotFoundException;
import mk.ukim.finki.gitcontributionanalyzer.model.*;
import mk.ukim.finki.gitcontributionanalyzer.service.AnalysisJobService;
import mk.ukim.finki.gitcontributionanalyzer.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@WebMvcTest(AnalysisController.class)
@ActiveProfiles("test")
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private AnalysisJobService analysisJobService;

    @Test
    void showsAnalysisForm() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Start analysis")));
    }

    @Test
    void rejectsInvalidFormBeforeStartingAnalysis() throws Exception {
        mockMvc.perform(post("/analyze")
                        .param("repositoryUrl", "not-a-repository")
                        .param("projectDescription", "short")
                        .param("email", "wrong"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeHasFieldErrors(
                        "analysisRequest",
                        "repositoryUrl",
                        "projectDescription",
                        "email"
                ));

        verify(analysisJobService, never()).startAnalysis(any());
    }

    @Test
    void queuesAValidRequestAndRedirectsToLiveProgress() throws Exception {
        UUID id = UUID.randomUUID();
        AnalysisJob queued = AnalysisJob.queued(id, OffsetDateTime.now());
        when(analysisJobService.startAnalysis(any())).thenReturn(queued);

        mockMvc.perform(post("/analyze")
                        .param("repositoryUrl", "https://github.com/team/project")
                        .param("projectDescription", "Team collaboration and organization application.")
                        .param("email", "mentor@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/analyses/" + id));

        verify(analysisJobService).startAnalysis(any());
        verify(reportService, never()).createReport(any());
    }

    @Test
    void rendersAnAccessibleLiveProgressPage() throws Exception {
        UUID id = UUID.randomUUID();
        AnalysisJob running = AnalysisJob.queued(id, OffsetDateTime.now())
                .advanceTo(AnalysisStage.ANALYZING_WITH_GEMINI, OffsetDateTime.now());
        when(analysisJobService.findById(id)).thenReturn(java.util.Optional.of(running));

        mockMvc.perform(get("/analyses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis-progress"))
                .andExpect(model().attribute("job", running))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("role=\"progressbar\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-valuenow=\"55\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Analyzing with Gemini")));
    }

    @Test
    void redirectsAnAlreadyCompletedProgressPageToTheReport() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        AnalysisJob completed = AnalysisJob.queued(id, OffsetDateTime.now())
                .complete(reportId, OffsetDateTime.now());
        when(analysisJobService.findById(id)).thenReturn(java.util.Optional.of(completed));

        mockMvc.perform(get("/analyses/{id}", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reports/" + reportId + "?newReport=true"));
    }

    @Test
    void returnsNoStoreJsonWhileAnalysisIsRunning() throws Exception {
        UUID id = UUID.randomUUID();
        AnalysisJob running = AnalysisJob.queued(id, OffsetDateTime.now())
                .advanceTo(AnalysisStage.READING_REPOSITORY, OffsetDateTime.now());
        when(analysisJobService.findById(id)).thenReturn(java.util.Optional.of(running));

        mockMvc.perform(get("/api/analyses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.stage").value("READING_REPOSITORY"))
                .andExpect(jsonPath("$.progress").value(10))
                .andExpect(jsonPath("$.reportUrl").doesNotExist());
    }

    @Test
    void completedStatusPointsToTheNewBrowserReport() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        AnalysisJob completed = AnalysisJob.queued(id, OffsetDateTime.now())
                .complete(reportId, OffsetDateTime.now());
        when(analysisJobService.findById(id)).thenReturn(java.util.Optional.of(completed));

        mockMvc.perform(get("/api/analyses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progress").value(100))
                .andExpect(jsonPath("$.reportUrl")
                        .value("/reports/" + reportId + "?newReport=true"));
    }

    @Test
    void failedStatusContainsAUserSafeMessageAndNoReportUrl() throws Exception {
        UUID id = UUID.randomUUID();
        AnalysisJob failed = AnalysisJob.queued(id, OffsetDateTime.now())
                .advanceTo(AnalysisStage.READING_REPOSITORY, OffsetDateTime.now())
                .fail("The repository could not be cloned.", OffsetDateTime.now());
        when(analysisJobService.findById(id)).thenReturn(java.util.Optional.of(failed));

        mockMvc.perform(get("/api/analyses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(AnalysisStatus.FAILED.name()))
                .andExpect(jsonPath("$.message").value("The repository could not be cloned."))
                .andExpect(jsonPath("$.reportUrl").doesNotExist());
    }

    @Test
    void returnsDirectNoStoreJsonForAMissingAnalysisStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(analysisJobService.findById(id)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/analyses/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error").value("Analysis not found."));
    }

    @Test
    void returnsDirectNoStoreJsonForAMalformedAnalysisStatusLink() throws Exception {
        mockMvc.perform(get("/api/analyses/not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error").value("Analysis not found."));
    }

    @Test
    void showsFriendlyPageForMissingAnalysisProgress() throws Exception {
        UUID id = UUID.randomUUID();
        when(analysisJobService.findById(id)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/analyses/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error-page"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Analysis not found")));
    }

    @Test
    void showsAControlledPageForAMalformedAnalysisProgressLink() throws Exception {
        mockMvc.perform(get("/analyses/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error-page"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid request")));
    }

    @Test
    void rendersCompleteReportPage() throws Exception {
        UUID id = UUID.randomUUID();
        when(reportService.getReport(id)).thenReturn(sampleReport(id));

        mockMvc.perform(get("/reports/{id}", id).param("newReport", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("report"))
                .andExpect(model().attribute("newReport", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ana Developer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("65%")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("High")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Final contribution assessment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Gemini AI")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("indicator-card info")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("new-report-note no-print")));
    }

    @Test
    void showsFriendlyPageForMissingReport() throws Exception {
        UUID id = UUID.randomUUID();
        when(reportService.getReport(id)).thenThrow(new ReportNotFoundException("Report not found."));

        mockMvc.perform(get("/reports/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error-page"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Report not found.")));
    }

    @Test
    void returnsNotFoundWithoutA500PageForMissingBrowserIcons() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void updatesTheLiveProgressMessageOnlyWhenItsContentChanges() throws Exception {
        mockMvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "progressMessage.textContent !== job.message"
                )));
    }

    private AnalysisReport sampleReport(UUID id) {
        ContributorAnalysis ana = new ContributorAnalysis(
                "Ana Developer",
                "ana@example.com",
                65,
                ContributionLevel.HIGH,
                "She implemented the core functionality.",
                List.of("Login", "User profile"),
                List.of(new CategorySummary(CommitCategory.FUNCTIONAL, 1, "New functionality")),
                List.of(new CommitAnalysis(
                        "1234567890abcdef",
                        "Add login",
                        CommitCategory.FUNCTIONAL,
                        5,
                        "Key project change."
                )),
                List.of()
        );
        ContributorAnalysis boris = new ContributorAnalysis(
                "Boris Tester",
                "boris@example.com",
                35,
                ContributionLevel.MEDIUM,
                "Added tests and fixes.",
                List.of("Integration tests"),
                List.of(new CategorySummary(CommitCategory.TESTING, 1, "Test coverage")),
                List.of(new CommitAnalysis(
                        "abcdef1234567890",
                        "Add tests",
                        CommitCategory.TESTING,
                        4,
                        "Improves reliability."
                )),
                List.of()
        );

        ContributionAnalysis analysis = new ContributionAnalysis(
                "Team collaboration application.",
                "The commits align with the project goal.",
                List.of(ana, boris),
                List.of(new TeamIndicator(
                        "BALANCE",
                        TeamIndicatorSeverity.INFO,
                        "Balanced contribution",
                        "No critical imbalance."
                )),
                "The team achieved the main goal.",
                "Gemini analyzed the commit messages, files, and diffs."
        );

        return new AnalysisReport(
                id,
                "https://github.com/team/project",
                "project",
                "main",
                "Team collaboration and organization application.",
                "mentor@example.com",
                AnalysisSource.GEMINI,
                "gemini-2.5-flash",
                "Gemini analyzed the Git history using the supplied project goal.",
                2,
                OffsetDateTime.now(),
                analysis,
                new EmailDelivery(EmailDeliveryStatus.SENT, "The report was sent.")
        );
    }
}