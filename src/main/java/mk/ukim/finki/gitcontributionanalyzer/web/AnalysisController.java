package mk.ukim.finki.gitcontributionanalyzer.web;
import jakarta.servlet.http.HttpServletResponse;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisJobStatusDto;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStage;
import mk.ukim.finki.gitcontributionanalyzer.enums.CommitCategory;
import mk.ukim.finki.gitcontributionanalyzer.exception.*;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisJob;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisRequest;
import jakarta.validation.Valid;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import mk.ukim.finki.gitcontributionanalyzer.service.AnalysisJobService;
import mk.ukim.finki.gitcontributionanalyzer.service.ReportPdfService;
import mk.ukim.finki.gitcontributionanalyzer.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Controller
public class AnalysisController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisController.class);

    public final ReportService reportService;
    private final AnalysisJobService analysisJobService;
    private final ReportPdfService reportPdfService;

    public AnalysisController(ReportService reportService, AnalysisJobService analysisJobService, ReportPdfService reportPdfService) {
        this.reportService = reportService;
        this.analysisJobService = analysisJobService;
        this.reportPdfService = reportPdfService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("analysisRequest", new AnalysisRequest());
        return "index";
    }

    @PostMapping("/analyze")
    public String startAnalysis(
            @Valid @ModelAttribute("analysisRequest") AnalysisRequest analysisRequest,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response) {

        if (bindingResult.hasErrors()) {
            return "index";
        }

        try {
            AnalysisJob job = analysisJobService.startAnalysis(analysisRequest);
            return "redirect:/analyses/" + job.id() + "?newAnalysis=true";
        } catch (Exception exception) {
            return unexpectedError(exception, model, response);
        }
    }

    @GetMapping("/analyses/{id}")
    public String showAnalysisProgress(
            @PathVariable String id,
            @RequestParam(name = "newAnalysis", defaultValue = "false") String newAnalysisValue,
            Model model,
            HttpServletResponse response) {
        UUID jobId;
        boolean replayFromStart;
        try {
            jobId = UUID.fromString(id);
            replayFromStart = parseBoolean(newAnalysisValue);
        } catch (IllegalArgumentException exception) {
            return invalidRequest(model, response);
        }

        try {
            AnalysisJob job = analysisJobService.findById(jobId)
                    .orElseThrow(() -> new AnalysisJobNotFoundException(
                            "The analysis was not found, or the application was restarted."
                    ));
            AnalysisJob presentationJob = replayFromStart
                    ? AnalysisJob.queued(job.id(), job.repositoryLabel(), job.createdAt())
                    : job;
            model.addAttribute("job", job);
            model.addAttribute("jobStatus", AnalysisJobStatusDto.from(job));
            model.addAttribute("presentationJob", presentationJob);
            model.addAttribute("presentationJobStatus", AnalysisJobStatusDto.from(presentationJob));
            model.addAttribute("replayFromStart", replayFromStart);
            model.addAttribute("analysisStages", AnalysisStage.values());
            return "analysis-progress";
        } catch (AnalysisJobNotFoundException exception) {
            return errorPage(
                    model,
                    response,
                    HttpStatus.NOT_FOUND,
                    "Analysis not found",
                    exception.getMessage()
            );
        } catch (Exception exception) {
            return unexpectedError(exception, model, response);
        }
    }

    @GetMapping("/api/analyses/{id}")
    @ResponseBody
    public ResponseEntity<?> getAnalysisStatus(@PathVariable String id) {
        UUID jobId;
        try {
            jobId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            return missingAnalysisStatus();
        }

        return analysisJobService.findById(jobId)
                .<ResponseEntity<?>>map(job -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(AnalysisJobStatusDto.from(job)))
                .orElseGet(this::missingAnalysisStatus);
    }

    @GetMapping("/reports/{id}")
    public String showReport(
            @PathVariable String id,
            @RequestParam(name = "newReport", defaultValue = "false") String newReportValue,
            Model model,
            HttpServletResponse response) {
        UUID reportId;
        boolean newReport;
        try {
            reportId = UUID.fromString(id);
            newReport = parseBoolean(newReportValue);
        } catch (IllegalArgumentException exception) {
            return invalidRequest(model, response);
        }

        try {
            AnalysisReport report = reportService.getReport(reportId);
            model.addAttribute("report", report);
            model.addAttribute("newReport", newReport);
            model.addAttribute("commitCategories", CommitCategory.values());
            model.addAttribute("previewMode", false);
            String reportPdfUrl = "/reports/" + reportId + "/pdf";
            model.addAttribute("reportPdfUrl", reportPdfUrl);
            model.addAttribute("reportPdfDownloadUrl", reportPdfUrl + "?download=true");
            return "report";
        } catch (ReportNotFoundException exception) {
            return errorPage(
                    model,
                    response,
                    HttpStatus.NOT_FOUND,
                    "Report not found",
                    exception.getMessage()
            );
        } catch (Exception exception) {
            return unexpectedError(exception, model, response);
        }
    }

    @GetMapping("/reports/{id}/pdf")
    @ResponseBody
    public ResponseEntity<?> showReportPdf(
            @PathVariable String id,
            @RequestParam(name = "download", defaultValue = "false") String downloadValue) {
        UUID reportId;
        boolean download;
        try {
            reportId = UUID.fromString(id);
            download = parseBoolean(downloadValue);
        } catch (IllegalArgumentException exception) {
            return pdfError(HttpStatus.BAD_REQUEST, "The requested PDF address contains an invalid value.");
        }

        try {
            AnalysisReport report = reportService.getReport(reportId);
            byte[] pdf = reportPdfService.createPdf(report);
            ContentDisposition disposition = (download
                    ? ContentDisposition.attachment()
                    : ContentDisposition.inline())
                    .filename(pdfFilename(report.repositoryName()), StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdf.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(pdf);
        } catch (ReportNotFoundException exception) {
            return pdfError(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (Exception exception) {
            LOGGER.error("Unexpected error while generating the PDF report", exception);
            return pdfError(HttpStatus.INTERNAL_SERVER_ERROR, "The PDF report could not be generated.");
        }
    }

    private ResponseEntity<?> missingAnalysisStatus() {
        return ResponseEntity.status(404)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("error", "Analysis not found."));
    }

    private ResponseEntity<Map<String, String>> pdfError(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Content-Type-Options", "nosniff")
                .body(Map.of("error", message));
    }

    private String pdfFilename(String repositoryName) {
        String safeName = repositoryName == null
                ? ""
                : repositoryName.strip().replaceAll("[^A-Za-z0-9._-]+", "-");
        safeName = safeName.replaceAll("^[._-]+|[._-]+$", "");
        if (safeName.isBlank()) {
            safeName = "report";
        } else if (safeName.length() > 80) {
            safeName = safeName.substring(0, 80);
        }
        return safeName + "-contribution-report.pdf";
    }

    private boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)
                || "on".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value)
                || "1".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)
                || "off".equalsIgnoreCase(value)
                || "no".equalsIgnoreCase(value)
                || "0".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean value.");
    }

    private String invalidRequest(Model model, HttpServletResponse response) {
        return errorPage(
                model,
                response,
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "The requested address contains an invalid value."
        );
    }

    private String unexpectedError(Exception exception, Model model, HttpServletResponse response) {
        LOGGER.error("Unexpected error while processing the request", exception);
        return errorPage(
                model,
                response,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                "Try again. If the problem continues, check the application terminal."
        );
    }

    private String errorPage(
            Model model,
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String message) {
        response.setStatus(status.value());
        model.addAttribute("status", status.value());
        model.addAttribute("title", title);
        model.addAttribute("message", message);
        model.addAttribute("errorHeading", switch (status) {
            case BAD_REQUEST -> "We couldn't process that request";
            case NOT_FOUND -> "We couldn't find that analysis";
            default -> "Something went wrong";
        });
        model.addAttribute("errorDescription", switch (status) {
            case NOT_FOUND -> "The analysis or report you're looking for doesn't exist or is no longer available. "
                    + "Reports are kept only for the current session and are not stored.";
            default -> message;
        });
        return "error-page";
    }
}