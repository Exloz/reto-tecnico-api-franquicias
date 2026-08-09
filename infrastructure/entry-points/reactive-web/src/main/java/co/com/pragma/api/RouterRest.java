package co.com.pragma.api;

import co.com.pragma.api.filter.RequestObservabilityFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Configuration
public class RouterRest {

    @Bean
    public RouterFunction<ServerResponse> routerFunction(Handler handler) {
        return RouterFunctions.route()
                .POST("/api/v1/franchises", observed("CreateFranchiseUseCase", handler::createFranchise))
                .POST("/api/v1/franchises/{franchiseId}/branches",
                        observed("AddBranchUseCase", handler::addBranch))
                .POST("/api/v1/franchises/{franchiseId}/branches/{branchId}/products",
                        observed("AddBranchProductUseCase", handler::addProduct))
                .DELETE("/api/v1/franchises/{franchiseId}/branches/{branchId}/products/{productId}",
                        observed("DeleteBranchProductUseCase", handler::deleteProduct))
                .PATCH("/api/v1/franchises/{franchiseId}/branches/{branchId}/products/{productId}/stock",
                        observed("UpdateBranchProductStockUseCase", handler::updateStock))
                .GET("/api/v1/franchises/{franchiseId}/branches/top-stock-products",
                        observed("FindTopStockProductsUseCase", handler::findTopStock))
                .PATCH("/api/v1/franchises/{franchiseId}",
                        observed("RenameFranchiseUseCase", handler::renameFranchise))
                .PATCH("/api/v1/franchises/{franchiseId}/branches/{branchId}",
                        observed("RenameBranchUseCase", handler::renameBranch))
                .PATCH("/api/v1/franchises/{franchiseId}/branches/{branchId}/products/{productId}",
                        observed("RenameBranchProductUseCase", handler::renameProduct))
                .build();
    }

    private <T extends ServerResponse> HandlerFunction<T> observed(String operation, HandlerFunction<T> handler) {
        return request -> Mono.just(request.exchange())
                .doOnNext(exchange -> exchange.getAttributes()
                        .put(RequestObservabilityFilter.OPERATION_ATTRIBUTE, operation))
                .then(handler.handle(request));
    }
}
