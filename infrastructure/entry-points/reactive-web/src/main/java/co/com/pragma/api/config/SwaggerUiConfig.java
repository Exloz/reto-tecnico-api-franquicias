package co.com.pragma.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;

@Configuration
@Profile("local")
public class SwaggerUiConfig {
    private static final String HTML = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Franchise API</title>
              <link rel="stylesheet" href="/swagger-ui/assets/swagger-ui.css">
            </head>
            <body>
              <div id="swagger-ui"></div>
              <script src="/swagger-ui/assets/swagger-ui-bundle.js"></script>
              <script src="/swagger-ui/assets/swagger-ui-standalone-preset.js"></script>
              <script src="/swagger-ui/franchise-initializer.js"></script>
            </body>
            </html>
            """;
    private static final String INITIALIZER = """
            window.onload = () => {
              window.ui = SwaggerUIBundle({
                url: '/openapi/franchise-api.yaml',
                dom_id: '#swagger-ui',
                deepLinking: true,
                presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
                layout: 'StandaloneLayout'
              });
            };
            """;

    @Bean
    RouterFunction<ServerResponse> swaggerUiRouter() {
        return RouterFunctions.route()
                .GET("/swagger-ui.html", request -> ServerResponse.temporaryRedirect(
                        URI.create("/swagger-ui/index.html")).build())
                .GET("/swagger-ui/index.html", request -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .bodyValue(HTML))
                .GET("/swagger-ui/franchise-initializer.js", request -> ServerResponse.ok()
                        .contentType(MediaType.valueOf("application/javascript"))
                        .bodyValue(INITIALIZER))
                .GET("/swagger-ui/assets/swagger-ui.css", request -> asset(
                        "swagger-ui.css", MediaType.valueOf("text/css")))
                .GET("/swagger-ui/assets/swagger-ui-bundle.js", request -> asset(
                        "swagger-ui-bundle.js", MediaType.valueOf("application/javascript")))
                .GET("/swagger-ui/assets/swagger-ui-standalone-preset.js", request -> asset(
                        "swagger-ui-standalone-preset.js", MediaType.valueOf("application/javascript")))
                .build();
    }

    private Mono<ServerResponse> asset(String name, MediaType mediaType) {
        return ServerResponse.ok()
                .contentType(mediaType)
                .body(BodyInserters.fromResource(new ClassPathResource(
                        "META-INF/resources/webjars/swagger-ui/5.32.11/" + name)));
    }
}
