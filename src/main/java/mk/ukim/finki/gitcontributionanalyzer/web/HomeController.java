package mk.ukim.finki.gitcontributionanalyzer.web;
import mk.ukim.finki.gitcontributionanalyzer.exception.*;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisReport;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisRequest;
import jakarta.validation.Valid;
import mk.ukim.finki.gitcontributionanalyzer.service.ReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.UUID;

@Controller
public class HomeController {

    public final ReportService reportService;

    public HomeController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("analysisRequest", new AnalysisRequest());
        return "index";
    }

    @PostMapping("/analyze")
    public String startAnalysis(
            @Valid @ModelAttribute AnalysisRequest analysisRequest,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if(bindingResult.hasErrors()) {
            return "index";
        }

        try{
            AnalysisReport report = reportService.createReport(analysisRequest);
            redirectAttributes.addFlashAttribute("newReport", true);
            return "redirect:/reports/" + report.id();
        } catch(RepositoryException | GeminiException e) {
            model.addAttribute("error", e.getMessage());
            return "index";
        }
    }

    @GetMapping("/reports/{id}")
    public String showReport(@org.springframework.web.bind.annotation.PathVariable UUID id, Model model) {
        model.addAttribute("report", reportService.getReport(id));
        return "report";
    }
}