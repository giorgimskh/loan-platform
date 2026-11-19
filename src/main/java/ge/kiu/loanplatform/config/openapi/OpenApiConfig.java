package ge.kiu.loanplatform.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Swagger UI (http://localhost:8080/swagger-ui.html) reads all @RestController classes by itself.
// This bean only gives the page its title and description.
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI loanPlatformOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Loan Platform API")
                .version("1.0")
                .description("Online loan applications for a bank: customers, loan products, "
                        + "applications, documents and reports."));
    }
}
