package mk.ukim.finki.gitcontributionanalyzer.web;
import jakarta.servlet.http.HttpServletResponse;
import mk.ukim.finki.gitcontributionanalyzer.exception.*;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import mk.ukim.finki.gitcontributionanalyzer.dto.AnalysisRequest;
import jakarta.validation.Valid;
import mk.ukim.finki.gitcontributionanalyzer.service.ReportService;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.ReportServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.UUID;

@Controller
public class AnalysisController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisController.class);

    public final ReportService reportService;

    public AnalysisController(ReportService reportService) {
        this.reportService = reportService;
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
            RedirectAttributes redirectAttributes,
            HttpServletResponse response) {

        if(bindingResult.hasErrors()) {
            return "index";
        }

        try{
            AnalysisReport report = reportService.createReport(analysisRequest);
            redirectAttributes.addFlashAttribute("newReport", true);
            return "redirect:/reports/" + report.id();
        } catch(RepositoryException e) {
            model.addAttribute("error", e.getMessage());
            return "index";
        } catch (Exception exception) {
            return unexpectedError(exception, model, response);
        }
    }

    @GetMapping("/reports/{id}")
    public String showReport(@PathVariable UUID id, Model model, HttpServletResponse response) {
        try {
            model.addAttribute("report", reportService.getReport(id));
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