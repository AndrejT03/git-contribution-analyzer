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
import java.util.List;
import java.util.UUID;
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-reveal-on-scroll")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-invalid=\"false\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/style.css?v=26.2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/app.js?v=26.2")));
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/style.css?v=26.2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/app.js?v=26.2")));
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
                        "data-current-stage=\"ANALYZING_WITH_GEMINI\""
                )))
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
                        "data-stage-name=\"ANALYZING_WITH_GEMINI\" data-stage-progress=\"55\" "
                                + "data-stage-label=\"Analyzing with Gemini\" "
                                + "data-stage-message=\"Classifying commits and assessing their alignment with the project goal.\" "
                                + "data-stage-state=\"SKIPPED\" class=\" is-skipped\""
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-stage-name=\"LOCAL_FALLBACK\" data-stage-progress=\"70\" "
                                + "data-stage-label=\"Running local fallback\" "
                                + "data-stage-message=\"Gemini is unavailable, so the deterministic local analyzer is continuing.\" "
                                + "data-stage-state=\"ACTIVE\" aria-current=\"step\" class=\" is-current\""
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
                .andExpect(jsonPath("$.progress").value(84))
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/style.css?v=26.2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/app.js?v=26.2")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("data-error-preview=\"400\""))));
    }

    @Test
    void rendersCompleteReportPage() throws Exception {
        UUID id = UUID.randomUUID();
        when(reportService.getReport(id)).thenReturn(sampleReport(id));

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/style.css?v=26.2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/app.js?v=26.2")));
    }

    @Test
    void opensAReportPdfInlineWithSafeResponseHeaders() throws Exception {
        UUID id = UUID.randomUUID();
        AnalysisReport report = sampleReport(id);
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
        AnalysisReport report = sampleReport(id);
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
    void servesTheVersionedAppleStylesheetInsteadOfAStaleRedesignAsset() throws Exception {
        mockMvc.perform(get("/css/style.css").param("v", "25.9"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("--blue: #0071e3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(".site-header")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "@media screen and (min-width: 1600px)"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("body.home-body")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("body.progress-body")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("body.report-body")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("body.error-body")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("zoom: 1.25")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "width: min(var(--wide-page-content-width), calc(100% - 40px))"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "width: min(var(--wide-focused-content-width), calc(100% - 8px))"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "width: min(var(--wide-page-content-width), 100%)"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("var(--progress-stop)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("@keyframes progress-aura")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        ".progress-experience.is-advancing"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        ".stage-list li.is-skipped"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(".report-action")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(".report-actions--header")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("font-size: 17px")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "oklch(57% 0.16 var(--contributor-hue, 210deg))"
                )));
    }

    @Test
    void updatesTheLiveProgressMessageOnlyWhenItsContentChanges() throws Exception {
        mockMvc.perform(get("/js/app.js"))
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
                        "if (isPreviewRoute && previewQuery.get(\"preview\") === \"reconnecting\")"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "const MIN_PROGRESS_TWEEN_MS = 800"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "const MAX_PROGRESS_TWEEN_MS = 2400"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "const MAX_PROGRESS_DELTA_PER_FRAME = 0.72"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Math.cos(Math.PI * elapsed)"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "const STAGE_HOLD_MS = 450"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "const INITIAL_STAGE_HOLD_MS = prefersReducedMotion ? 0 : 650"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "progressRoot.dataset.replayFromStart === \"true\""
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "window.matchMedia?.(\"(prefers-reduced-motion: reduce)\")"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "window.requestAnimationFrame"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "window.location.replace(reportUrl)"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "const animateProgressTo = (targetProgress)"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "progressRoot.classList.add(\"is-advancing\")"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "job.stageStates?.[item.dataset.stageName]"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "const renderStatusSequence = async (job)"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "await renderStatusSequence(job)"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "window.setTimeout(pollStatus, INITIAL_STAGE_HOLD_MS)"
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
                List.of("Primary finding", "Secondary finding")
        );
        ContributorAnalysis boris = new ContributorAnalysis(
                "Boris Tester",
                "boris@example.com",
                35,
                ContributionLevel.HIGH,
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
                "gemini-3.7-flash",
                "Gemini analyzed the Git history using the supplied project goal.",
                2,
                OffsetDateTime.now(),
                analysis,
                new EmailDelivery(EmailDeliveryStatus.SENT, "The report was sent.")
        );
    }
}