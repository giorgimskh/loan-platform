package ge.kiu.loanplatform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// Reads every "app.*" property from application.properties.
// app.upload-dir -> uploadDir (Spring converts kebab-case to camelCase)
@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private String uploadDir;
}
