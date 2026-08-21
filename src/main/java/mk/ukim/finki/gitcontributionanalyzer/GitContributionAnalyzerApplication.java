package mk.ukim.finki.gitcontributionanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GitContributionAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitContributionAnalyzerApplication.class, args);
    }

}