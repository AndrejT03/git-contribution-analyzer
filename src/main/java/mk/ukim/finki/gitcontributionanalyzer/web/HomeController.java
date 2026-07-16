package mk.ukim.finki.gitcontributionanalyzer.web;
import mk.ukim.finki.gitcontributionanalyzer.model.AnalysisRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("analysisRequest", new AnalysisRequest());
        return "index";
    }

    @PostMapping("/analyze")
    public String analyze(
            @Valid @ModelAttribute AnalysisRequest analysisRequest,
            BindingResult bindingResult,
            Model model) {

        if(bindingResult.hasErrors()) {
            return "index";
        }

        model.addAttribute("message", "The data is valid and the analysis can start.");
        return "index";
    }
}