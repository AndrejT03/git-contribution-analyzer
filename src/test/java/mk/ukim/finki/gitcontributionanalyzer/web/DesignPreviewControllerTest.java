package mk.ukim.finki.gitcontributionanalyzer.web;
import mk.ukim.finki.gitcontributionanalyzer.service.ReportPdfService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DesignPreviewController.class)
@ActiveProfiles("test")
class DesignPreviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportPdfService reportPdfService;

    @Test
    void loadsTheIsolatedPreviewScriptOnProgressFixtures() throws Exception {
        mockMvc.perform(get("/__preview/progress").param("chrome", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/js/design-preview.js?v=28.0")))
                .andExpect(content().string(containsString("/js/app.js?v=28.0")))
                .andExpect(content().string(containsString("data-progress-preview=\"failure\"")))
                .andExpect(content().string(not(containsString("data-current-stage="))))
                .andExpect(content().string(not(containsString("data-stage-state="))));
    }

    @Test
    void loadsTheIsolatedPreviewScriptOnErrorFixtures() throws Exception {
        mockMvc.perform(get("/__preview/error"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/js/design-preview.js?v=28.0")))
                .andExpect(content().string(containsString("data-error-preview=\"404\"")));
    }

    @Test
    void servesSyntheticProgressStatesFromThePreviewOnlyAsset() throws Exception {
        mockMvc.perform(get("/js/design-preview.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("initializeProgressPreview")))
                .andExpect(content().string(containsString("ANALYZING_WITH_GEMINI: \"ACTIVE\"")))
                .andExpect(content().string(containsString("initializeErrorPreview")));
    }
}