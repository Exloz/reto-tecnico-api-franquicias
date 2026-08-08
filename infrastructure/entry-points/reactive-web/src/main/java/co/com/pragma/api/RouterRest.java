package co.com.pragma.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class RouterRest {

    @Bean
    public RouterFunction<ServerResponse> routerFunction(Handler handler) {
        return RouterFunctions.route()
                .POST("/api/v1/franchises", handler::createFranchise)
                .POST("/api/v1/franchises/{franchiseId}/branches", handler::addBranch)
                .POST("/api/v1/franchises/{franchiseId}/branches/{branchId}/products", handler::addProduct)
                .DELETE("/api/v1/franchises/{franchiseId}/branches/{branchId}/products/{productId}",
                        handler::deleteProduct)
                .PATCH("/api/v1/franchises/{franchiseId}/branches/{branchId}/products/{productId}/stock",
                        handler::updateStock)
                .GET("/api/v1/franchises/{franchiseId}/branches/top-stock-products", handler::findTopStock)
                .PATCH("/api/v1/franchises/{franchiseId}", handler::renameFranchise)
                .PATCH("/api/v1/franchises/{franchiseId}/branches/{branchId}", handler::renameBranch)
                .PATCH("/api/v1/franchises/{franchiseId}/branches/{branchId}/products/{productId}",
                        handler::renameProduct)
                .build();
    }
}
