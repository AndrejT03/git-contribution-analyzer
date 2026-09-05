package mk.ukim.finki.gitcontributionanalyzer.config;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import static org.assertj.core.api.Assertions.assertThat;

class AppSettingsTest {

    private static final List<String> APP_PROPERTY_NAMES = List.of(
            "app.max-commits",
            "app.max-diff-chars",
            "app.git-timeout-seconds",
            "app.ai-timeout-seconds",
            "app.mail-enabled",
            "app.mail-from"
    );

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SettingsConfiguration.class);

    @Test
    void bindsDocumentedDefaults() throws IOException {
        withApplicationDefaults().run(context -> {
            assertThat(context).hasNotFailed();
            AppSettings settings = context.getBean(AppSettings.class);
            assertThat(settings.maxCommits()).isEqualTo(80);
            assertThat(settings.maxDiffChars()).isEqualTo(6000);
            assertThat(settings.gitTimeoutSeconds()).isEqualTo(120);
            assertThat(settings.aiTimeoutSeconds()).isEqualTo(180);
            assertThat(settings.mailEnabled()).isFalse();
            assertThat(settings.mailFrom()).isEmpty();
        });
    }

    @Test
    void bindsExternalValuesAndNormalizesMailAddress() throws IOException {
        withApplicationDefaults()
                .withPropertyValues(
                        "app.max-commits=55",
                        "app.max-diff-chars=7500",
                        "app.git-timeout-seconds=90",
                        "app.ai-timeout-seconds=75",
                        "app.mail-enabled=true",
                        "app.mail-from=  reports@example.com  "
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AppSettings settings = context.getBean(AppSettings.class);
                    assertThat(settings.maxCommits()).isEqualTo(55);
                    assertThat(settings.maxDiffChars()).isEqualTo(7500);
                    assertThat(settings.gitTimeoutSeconds()).isEqualTo(90);
                    assertThat(settings.aiTimeoutSeconds()).isEqualTo(75);
                    assertThat(settings.mailEnabled()).isTrue();
                    assertThat(settings.mailFrom()).isEqualTo("reports@example.com");
                });
    }

    @Test
    void rejectsValuesOutsideTheDocumentedBounds() throws IOException {
        withApplicationDefaults()
                .withPropertyValues("app.max-commits=201")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("maxCommits")
                            .hasMessageContaining("200");
                });
    }

    @Test
    void keepsProviderCredentialsOutOfApplicationConfiguration() throws IOException {
        Properties properties = PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("application.properties")
        );

        assertThat(properties)
                .containsKey("app.ai-timeout-seconds")
                .doesNotContainKeys(
                        "spring.config.import",
                        "app.gemini-api-key",
                        "app.gemini-model",
                        "app.gemini-timeout-seconds"
                );
    }

    private ApplicationContextRunner withApplicationDefaults() throws IOException {
        Properties properties = PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("application.properties")
        );
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addLast(new PropertiesPropertySource("applicationDefaults", properties));
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);

        String[] resolvedValues = APP_PROPERTY_NAMES.stream()
                .map(name -> name + "=" + resolver.getRequiredProperty(name))
                .toArray(String[]::new);
        return contextRunner.withPropertyValues(resolvedValues);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppSettings.class)
    static class SettingsConfiguration {
    }
}