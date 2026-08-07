package mk.ukim.finki.gitcontributionanalyzer.web;
import jakarta.servlet.http.HttpServletResponse;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisJobStatus;
import mk.ukim.finki.gitcontributionanalyzer.enums.AnalysisStatus;
import mk.ukim.finki.gitcontributionanalyzer.exception.*;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisJob;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisRequest;
import jakarta.validation.Valid;
import mk.ukim.finki.gitcontributionanalyzer.service.AnalysisJobService;
import mk.ukim.finki.gitcontributionanalyzer.service.ReportService;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.ReportServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.UUID;

@Controller
public class AnalysisController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisController.class);

    public final ReportService reportService;
    private final AnalysisJobService analysisJobService;

    public AnalysisController(ReportService reportService, AnalysisJobService analysisJobService) {
        this.reportService = reportService;
        this.analysisJobService = analysisJobService;
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

        if(bindingResult.hasErrors()) {
            return "index";
        }

        try{
            AnalysisJob job = analysisJobService.startAnalysis(analysisRequest);
            return "redirect:/analyses/" + job.id();
        } catch (Exception exception) {
            return unexpectedError(exception, model, response);
        }
    }

    @GetMapping("/analyses/{id}")
    public String showAnalysisProgress(
            @PathVariable String id,
            Model model,
            HttpServletResponse response) {
        UUID jobId;
        try {
            jobId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            return invalidRequest(model, response);
        }

        try {
            AnalysisJob job = analysisJobService.findById(jobId)
                    .orElseThrow(() -> new AnalysisJobNotFoundException(
                            "The analysis was not found, or the application was restarted."
                    ));
            if (job.status() == AnalysisStatus.COMPLETED && job.reportId() != null) {
                return "redirect:/reports/" + job.reportId() + "?newReport=true";
            }
            model.addAttribute("job", job);
            model.addAttribute("jobStatus", AnalysisJobStatus.from(job));
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
                        .body(AnalysisJobStatus.from(job)))
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
            model.addAttribute("report", reportService.getReport(reportId));
            model.addAttribute("newReport", newReport);
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

    private ResponseEntity<?> missingAnalysisStatus() {
        return ResponseEntity.status(404)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("error", "Analysis not found."));
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
        return "error-page";
    }
}