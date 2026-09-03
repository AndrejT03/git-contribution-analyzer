package mk.ukim.finki.gitcontributionanalyzer.web;
import mk.ukim.finki.gitcontributionanalyzer.dto.*;
import mk.ukim.finki.gitcontributionanalyzer.enums.*;
import mk.ukim.finki.gitcontributionanalyzer.exception.ReportNotFoundException;
import mk.ukim.finki.gitcontributionanalyzer.model.*;
import mk.ukim.finki.gitcontributionanalyzer.service.AnalysisJobService;
import mk.ukim.finki.gitcontributionanalyzer.service.ReportPdfService;
import mk.ukim.finki.gitcontributionanalyzer.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import static mk.ukim.finki.gitcontributionanalyzer.support.TestDataFactory.richAnalysisReport;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @MockitoBean
    private ReportPdfService reportPdfService;

    @Test
    void showsAnalysisForm() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Who really contributed")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Start analysis")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("How it works")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("descriptionCounter")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"aiKey\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"aiKey\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("type=\"password\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("maxlength=\"1024\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"aiKeyVisibility\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"aiModel\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"aiModel\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("label=\"OpenAI\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("label=\"Anthropic Claude\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("label=\"OpenRouter\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("openai::gpt-5.6-terra")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AI API key are used only")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your selected AI model classifies")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-reveal-on-scroll")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-invalid=\"false\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/style.css?v=28.0")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/app.js?v=28.0")));
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
                ))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("form-field--invalid")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-invalid=\"true\"")));

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
                        .param("email", "mentor@example.com")
                        .param("aiKey", "test-key-never-persisted")
                        .param("aiModel", "openai::gpt-5.6-terra"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/analyses/" + id + "?newAnalysis=true"));

        verify(analysisJobService).startAnalysis(any());
        verifyNoInteractions(reportService);
    }

    @Test
    void rendersAnAccessibleLiveProgressPage() throws Exception {
        UUID id = UUID.randomUUID();
        AnalysisJob running = AnalysisJob.queued(id, "orbital-labs/flightdeck", OffsetDateTime.now())
                .advanceTo(AnalysisStage.ANALYZING_WITH_GEMINI, OffsetDateTime.now());
        when(analysisJobService.findById(id)).thenReturn(java.util.Optional.of(running));

        mockMvc.perform(get("/analyses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis-progress"))
                .andExpect(model().attribute("job", running))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("role=\"progressbar\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-valuenow=\"55\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("orbital-labs/flightdeck")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-stage-name=\"COMPLETED\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Analyzing with Gemini")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Progress follows real work."
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "A stage is active while it runs"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("id=\"progressStageNumber\"")
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-reveal-on-scroll")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/style.css?v=28.0")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/app.js?v=28.0")));
    }

    @Test
    void replaysANewAnalysisFromQueuedWhileKeepingTheActualJobAsTheApiTarget() throws Exception {
        UUID id = UUID.randomUUID();
        AnalysisJob running = AnalysisJob.queued(id, "orbital-labs/flightdeck", OffsetDateTime.now())
                .advanceTo(AnalysisStage.STARTING, OffsetDateTime.now())
                .advanceTo(AnalysisStage.READING_REPOSITORY, OffsetDateTime.now())
                .advanceTo(AnalysisStage.ANALYZING_WITH_GEMINI, OffsetDateTime.now());
        when(analysisJobService.findById(id)).thenReturn(java.util.Optional.of(running));

        mockMvc.perform(get("/analyses/{id}", id).param("newAnalysis", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis-progress"))
                .andExpect(model().attribute("job", running))
                .andExpect(model().attribute("replayFromStart", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-valuenow=\"0\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-replay-from-start=\"true\""
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<strong id=\"progressPercent\">0</strong>"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("id=\"progressStageNumber\"")
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<h1 id=\"progressStage\">Queued</h1>"
                )));
    }

    @Test
    void rendersAnAlreadyCompletedProgressPageBeforeTheDelayedReportRedirect() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        AnalysisJob completed = AnalysisJob.queued(id, OffsetDateTime.now())
                .complete(reportId, OffsetDateTime.now());
        when(analysisJobService.findById(id)).thenReturn(java.util.Optional.of(completed));

        mockMvc.perform(get("/analyses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis-progress"))
                .andExpect(model().attribute("job", completed))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-valuenow=\"100\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-initial-status=\"COMPLETED\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-report-url=\"/reports/" + reportId + "?newReport=true\""
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Analysis complete")));
    }

    @Test
    void replaysAJustCompletedNewAnalysisFromZeroBeforeRedirectingToItsReport() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        AnalysisJob completed = AnalysisJob.queued(id, "team/project", now)
                .advanceTo(AnalysisStage.STARTING, now.plusSeconds(1))
                .advanceTo(AnalysisStage.READING_REPOSITORY, now.plusSeconds(2))
                .advanceTo(AnalysisStage.ANALYZING_WITH_GEMINI, now.plusSeconds(3))
                .complete(reportId, AnalysisSource.GEMINI, now.plusSeconds(4));
        when(analysisJobService.findById(id)).thenReturn(java.util.Optional.of(completed));

        mockMvc.perform(get("/analyses/{id}", id).param("newAnalysis", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis-progress"))
                .andExpect(model().attribute("replayFromStart", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-valuenow=\"0\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-initial-status=\"COMPLETED\""
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-replay-from-start=\"true\""
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-report-url=\"/reports/" + reportId + "?newReport=true\""
                )));
    }

    @Test
    void returnsNoStoreJsonWhileAnalysisIsRunning() throws Exception {
        UUID id = UUID.randomUUID();
        AnalysisJob running = AnalysisJob.queued(id, "team/project", OffsetDateTime.now())
                .advanceTo(AnalysisStage.READING_REPOSITORY, OffsetDateTime.now());
        when(analysisJobService.findById(id)).thenReturn(java.util.Optional.of(running));

        mockMvc.perform(get("/api/analyses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.stage").value("READING_REPOSITORY"))
                .andExpect(jsonPath("$.progress").value(10))
                .andExpect(jsonPath("$.repositoryLabel").value("team/project"))
                .andExpect(jsonPath("$.reportUrl").doesNotExist());
    }

    @Test
    void rendersGeminiAsSkippedAndLocalFallbackAsActive() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        AnalysisJob fallback = AnalysisJob.queued(id, "team/project", now)
                .advanceTo(AnalysisStage.STARTING, now.plusSeconds(1))
                .advanceTo(AnalysisStage.READING_REPOSITORY, now.plusSeconds(2))
                .advanceTo(AnalysisStage.ANALYZING_WITH_GEMINI, now.plusSeconds(3))
                .selectAnalysisSource(AnalysisSource.LOCAL_FALLBACK, now.plusSeconds(4))
                .advanceTo(AnalysisStage.LOCAL_FALLBACK, now.plusSeconds(5));
        when(analysisJobService.findById(id)).thenReturn(java.util.Optional.of(fallback));

        mockMvc.perform(get("/analyses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-stage-name=\"ANALYZING_WITH_GEMINI\""
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\" is-skipped\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-stage-name=\"LOCAL_FALLBACK\""
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "aria-current=\"step\" class=\" is-current\""
                )));
    }

    @Test
    void returnsDurableFallbackStageStatesAfterFastIntermediateUpdates() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        AnalysisJob fallback = AnalysisJob.queued(id, "team/project", now)
                .advanceTo(AnalysisStage.STARTING, now.plusSeconds(1))
                .advanceTo(AnalysisStage.READING_REPOSITORY, now.plusSeconds(2))
                .advanceTo(AnalysisStage.ANALYZING_WITH_GEMINI, now.plusSeconds(3))
                .selectAnalysisSource(AnalysisSource.LOCAL_FALLBACK, now.plusSeconds(4))
                .advanceTo(AnalysisStage.LOCAL_FALLBACK, now.plusSeconds(5))
                .advanceTo(AnalysisStage.PREPARING_REPORT, now.plusSeconds(6));
        when(analysisJobService.findById(id)).thenReturn(java.util.Optional.of(fallback));

        mockMvc.perform(get("/api/analyses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(82))
                .andExpect(jsonPath("$.analysisSource").value("LOCAL_FALLBACK"))
                .andExpect(jsonPath("$.stageHistory[3]").value("ANALYZING_WITH_GEMINI"))
                .andExpect(jsonPath("$.stageHistory[4]").value("LOCAL_FALLBACK"))
                .andExpect(jsonPath("$.stageStates.ANALYZING_WITH_GEMINI").value("SKIPPED"))
                .andExpect(jsonPath("$.stageStates.LOCAL_FALLBACK").value("COMPLETE"))
                .andExpect(jsonPath("$.stageStates.PREPARING_REPORT").value("ACTIVE"));
    }

    @Test
    void completedStatusPointsToTheNewBrowserReport() throws Exception {
        UUID id = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        AnalysisJob completed = AnalysisJob.queued(id, now)
                .advanceTo(AnalysisStage.STARTING, now.plusSeconds(1))
                .advanceTo(AnalysisStage.READING_REPOSITORY, now.plusSeconds(2))
                .advanceTo(AnalysisStage.ANALYZING_WITH_GEMINI, now.plusSeconds(3))
                .selectAnalysisSource(AnalysisSource.GEMINI, now.plusSeconds(4))
                .advanceTo(AnalysisStage.PREPARING_REPORT, now.plusSeconds(5))
                .complete(reportId, AnalysisSource.GEMINI, now.plusSeconds(6));
        when(analysisJobService.findById(id)).thenReturn(java.util.Optional.of(completed));

        mockMvc.perform(get("/api/analyses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progress").value(100))
                .andExpect(jsonPath("$.stageStates.ANALYZING_WITH_GEMINI").value("COMPLETE"))
                .andExpect(jsonPath("$.stageStates.LOCAL_FALLBACK").value("SKIPPED"))
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
                .andExpect(jsonPath("$.status").value(AnalysisJobStatus.FAILED.name()))
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("We couldn&#39;t find that analysis")));
    }

    @Test
    void showsAControlledPageForAMalformedAnalysisProgressLink() throws Exception {
        mockMvc.perform(get("/analyses/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error-page"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("We couldn&#39;t process that request")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/style.css?v=28.0")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/app.js?v=28.0")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("data-error-preview=\"400\""))));
    }

    @Test
    void rejectsMalformedReplayFlagsBeforeLookingUpAnAnalysis() throws Exception {
        mockMvc.perform(get("/analyses/{id}", UUID.randomUUID()).param("newAnalysis", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error-page"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "We couldn&#39;t process that request"
                )));

        verifyNoInteractions(analysisJobService);
    }

    @Test
    void rendersCompleteReportPage() throws Exception {
        UUID id = UUID.randomUUID();
        when(reportService.getReport(id)).thenReturn(richAnalysisReport(id));

        mockMvc.perform(get("/reports/{id}", id).param("newReport", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("report"))
                .andExpect(model().attribute("newReport", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<header class=\"site-header site-header--quiet no-print\">"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<a class=\"site-brand\" href=\"/\" aria-label=\"Git Contribution AI home\">"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<nav class=\"report-actions report-actions--header no-print\" aria-label=\"PDF report actions\">"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "aria-label=\"Open report PDF in a new tab\""
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "aria-label=\"Download report PDF\""
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("class=\"report-actions no-print\"")
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ana Developer")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("65%")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("High")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Final contribution assessment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Gemini AI")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Contribution overview")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("contribution-note high")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Primary finding")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Secondary finding")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Balanced contribution")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("team-indicators")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("report-alert report-alert--success")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Commit evidence")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<b>1</b>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<span>highlight</span>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("shown")
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("--contributor-hue: 210")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("--contributor-hue: 347.508")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Open PDF")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Download PDF")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/reports/" + id + "/pdf"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/reports/" + id + "/pdf?download=true"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-reveal-on-scroll")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/style.css?v=28.0")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/app.js?v=28.0")));
    }

    @Test
    void opensAReportPdfInlineWithSafeResponseHeaders() throws Exception {
        UUID id = UUID.randomUUID();
        AnalysisReport report = richAnalysisReport(id);
        byte[] pdf = "%PDF-1.7\npreview".getBytes(StandardCharsets.US_ASCII);
        when(reportService.getReport(id)).thenReturn(report);
        when(reportPdfService.createPdf(report)).thenReturn(pdf);

        mockMvc.perform(get("/reports/{id}/pdf", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdf))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.startsWith("inline;"),
                                org.hamcrest.Matchers.containsString("project-contribution-report.pdf")
                        )
                ));

        verify(reportPdfService).createPdf(report);
    }

    @Test
    void downloadsTheSameReportPdfAsAnAttachment() throws Exception {
        UUID id = UUID.randomUUID();
        AnalysisReport report = richAnalysisReport(id);
        byte[] pdf = "%PDF-1.7\ndownload".getBytes(StandardCharsets.US_ASCII);
        when(reportService.getReport(id)).thenReturn(report);
        when(reportPdfService.createPdf(report)).thenReturn(pdf);

        mockMvc.perform(get("/reports/{id}/pdf", id).param("download", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdf))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment;")
                ));
    }

    @Test
    void returnsNotFoundWithoutTryingToGenerateAMissingPdfReport() throws Exception {
        UUID id = UUID.randomUUID();
        when(reportService.getReport(id)).thenThrow(new ReportNotFoundException("Report not found."));

        mockMvc.perform(get("/reports/{id}/pdf", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error").value("Report not found."));

        verifyNoInteractions(reportPdfService);
    }

    @Test
    void rejectsMalformedPdfLinksBeforeLookingUpAReport() throws Exception {
        mockMvc.perform(get("/reports/not-a-uuid/pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(
                        "The requested PDF address contains an invalid value."
                ));

        verifyNoInteractions(reportService, reportPdfService);
    }

    @Test
    void rejectsMalformedPdfDownloadFlagsBeforeLookingUpAReport() throws Exception {
        mockMvc.perform(get("/reports/{id}/pdf", UUID.randomUUID()).param("download", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(
                        "The requested PDF address contains an invalid value."
                ));

        verifyNoInteractions(reportService, reportPdfService);
    }

    @Test
    void showsFriendlyPageForMissingReport() throws Exception {
        UUID id = UUID.randomUUID();
        when(reportService.getReport(id)).thenThrow(new ReportNotFoundException("Report not found."));

        mockMvc.perform(get("/reports/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error-page"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("We couldn&#39;t find that analysis")));
    }

    @Test
    void returnsNotFoundWithoutA500PageForMissingBrowserIcons() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void servesTheVersionedPhaseTwentyEightStylesheetWithCoreInteractionStates() throws Exception {
        mockMvc.perform(get("/css/style.css").param("v", "28.0"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("--blue: #0071e3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("body.home-body")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("body.progress-body")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("body.report-body")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("body.error-body")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        ".progress-experience.is-advancing"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        ".stage-list li.is-skipped"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("--field-border: #8e8e93")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(".ai-settings-grid")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(".secret-input__toggle")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(".form-field select:focus")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(".reveal-ready [data-reveal]")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("prefers-reduced-motion: reduce")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "oklch(57% 0.16 var(--contributor-hue, 210deg))"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("--progress-stop")
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("@keyframes progress-aura")
                )));
    }

    @Test
    void servesLiveProgressReplayAndAuthoritativeStageStateContracts() throws Exception {
        mockMvc.perform(get("/js/app.js").param("v", "28.0"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "if (element && element.textContent !== text)"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "updateText(progressMessage, job.message)"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "progressRoot.classList.add(\"is-reconnecting\")"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "const INITIAL_STAGE_HOLD_MS = motionIsReduced ? 0 : 650"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "progressRoot.dataset.replayFromStart === \"true\""
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "window.matchMedia?.(\"(prefers-reduced-motion: reduce)\")"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "window.location.replace(reportUrl)"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "job.stageStates[item.dataset.stageName]"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "const renderStatusSequence = async (job)"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "job.stageHistory ?? [job.stage]"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "aiModel.addEventListener(\"change\", updateSelectedProvider)"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "aiKey.type = visible ? \"text\" : \"password\""
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "API key cleared because the provider changed"
                )));
    }

    @Test
    void servesPreviewFixturesSeparatelyFromTheProductionScript() throws Exception {
        mockMvc.perform(get("/js/design-preview.js").param("v", "28.0"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "window.gitContributionDesignPreview"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "previewQuery.get(\"preview\") === \"sequence\""
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "previewQuery.get(\"preview\") === \"reconnecting\""
                )));
    }
}