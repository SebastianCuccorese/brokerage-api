package dev.cuccorese.brokerage.shared;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata of the generated OpenAPI document; endpoints are discovered from the controllers. */
@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI openApi(@Value("${app.version}") String version) {
        return new OpenAPI().info(new Info()
                .title("brokerage-api")
                .description("Brokerage API: portfolio, instrument search, orders and cash transfers")
                .version(version));
    }
}
