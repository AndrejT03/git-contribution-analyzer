package mk.ukim.finki.gitcontributionanalyzer.web;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.json.JacksonJsonView;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Controller
public class AnalysisController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisController.class);

    private final ReportService reportService;
    private final AnalysisJobService analysisJobService;
    private final ReportPdfService reportPdfService;

    public AnalysisController(
            ReportService reportService,
            AnalysisJobService analysisJobService,
            ReportPdfService reportPdfService) {
        this.reportService = reportService;
        this.analysisJobService = analysisJobService;
        this.reportPdfService = reportPdfService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("analysisRequest", AnalysisRequest.empty());
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
            @PathVariable UUID id,
            @RequestParam(name = "newAnalysis", defaultValue = "false") boolean replayFromStart,
            Model model,
            HttpServletResponse response) {
        try {
            AnalysisJob job = analysisJobService.findById(id).orElse(null);
            if (job == null) {
                return errorPage(
                        model,
                        response,
                        HttpStatus.NOT_FOUND,
                        "The analysis was not found, or the application was restarted."
                );
            }
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
        } catch (Exception exception) {
            return unexpectedError(exception, model, response);
        }
    }

    @GetMapping("/api/analyses/{id}")
    @ResponseBody
    public ResponseEntity<?> getAnalysisStatus(@PathVariable UUID id) {
        return analysisJobService.findById(id)
                .<ResponseEntity<?>>map(job -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(AnalysisJobStatusDto.from(job)))
                .orElseGet(this::missingAnalysisStatus);
    }

    @GetMapping("/reports/{id}")
    public String showReport(
            @PathVariable UUID id,
            @RequestParam(name = "newReport", defaultValue = "false") boolean newReport,
            Model model,
            HttpServletResponse response) {
        try {
            AnalysisReport report = reportService.getReport(id);
            model.addAttribute("report", report);
            model.addAttribute("newReport", newReport);
            model.addAttribute("commitCategories", CommitCategory.values());
            model.addAttribute("previewMode", false);
            String reportPdfUrl = "/reports/" + id + "/pdf";
            model.addAttribute("reportPdfUrl", reportPdfUrl);
            model.addAttribute("reportPdfDownloadUrl", reportPdfUrl + "?download=true");
            return "report";
        } catch (ReportNotFoundException exception) {
            return errorPage(
                    model,
                    response,
                    HttpStatus.NOT_FOUND,
                    exception.getMessage()
            );
        } catch (Exception exception) {
            return unexpectedError(exception, model, response);
        }
    }

    @GetMapping("/reports/{id}/pdf")
    @ResponseBody
    public ResponseEntity<?> showReportPdf(
            @PathVariable UUID id,
            @RequestParam(name = "download", defaultValue = "false") boolean download) {
        try {
            AnalysisReport report = reportService.getReport(id);
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

    private String unexpectedError(Exception exception, Model model, HttpServletResponse response) {
        LOGGER.error("Unexpected error while processing the request", exception);
        return errorPage(
                model,
                response,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Try again. If the problem continues, check the application terminal."
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView handleInvalidRouteValue(
            HttpServletRequest request,
            HttpServletResponse response) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/analyses/")) {
            return jsonErrorView(response, HttpStatus.NOT_FOUND, "Analysis not found.", false);
        }
        if (path.endsWith("/pdf")) {
            return jsonErrorView(
                    response,
                    HttpStatus.BAD_REQUEST,
                    "The requested PDF address contains an invalid value.",
                    true
            );
        }
        return new ModelAndView(
                "error-page",
                errorAttributes(HttpStatus.BAD_REQUEST, "The requested address contains an invalid value."),
                HttpStatus.BAD_REQUEST
        );
    }

    private ModelAndView jsonErrorView(
            HttpServletResponse response,
            HttpStatus status,
            String message,
            boolean preventContentTypeSniffing) {
        if (preventContentTypeSniffing) {
            response.setHeader("X-Content-Type-Options", "nosniff");
        }

        ModelAndView result = new ModelAndView(
                new JacksonJsonView(),
                Map.of("error", message)
        );
        result.setStatus(status);
        return result;
    }

    private String errorPage(
            Model model,
            HttpServletResponse response,
            HttpStatus status,
            String message) {
        response.setStatus(status.value());
        model.addAllAttributes(errorAttributes(status, message));
        return "error-page";
    }

    private Map<String, Object> errorAttributes(HttpStatus status, String message) {
        String heading = switch (status) {
            case BAD_REQUEST -> "We couldn't process that request";
            case NOT_FOUND -> "We couldn't find that analysis";
            default -> "Something went wrong";
        };
        String description = status == HttpStatus.NOT_FOUND
                ? "The analysis or report you're looking for doesn't exist or is no longer available. "
                + "Reports are kept only for the current session and are not stored."
                : message;
        return Map.of(
                "status", status.value(),
                "errorHeading", heading,
                "errorDescription", description
        );
    }
}