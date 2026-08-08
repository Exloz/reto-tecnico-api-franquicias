package co.com.pragma.api;

import co.com.pragma.api.dto.ApiMapper;
import co.com.pragma.api.dto.ApiRequests;
import co.com.pragma.api.error.InvalidRequestException;
import co.com.pragma.api.pagination.CursorCodec;
import co.com.pragma.usecase.addbranch.AddBranchUseCase;
import co.com.pragma.usecase.addbranchproduct.AddBranchProductUseCase;
import co.com.pragma.usecase.createfranchise.CreateFranchiseUseCase;
import co.com.pragma.usecase.deletebranchproduct.DeleteBranchProductUseCase;
import co.com.pragma.usecase.findtopstockproducts.FindTopStockProductsUseCase;
import co.com.pragma.usecase.renamebranch.RenameBranchUseCase;
import co.com.pragma.usecase.renamebranchproduct.RenameBranchProductUseCase;
import co.com.pragma.usecase.renamefranchise.RenameFranchiseUseCase;
import co.com.pragma.usecase.updatebranchproductstock.UpdateBranchProductStockUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class Handler {
    private static final int DEFAULT_PAGE_LIMIT = 50;
    private static final int MAXIMUM_PAGE_LIMIT = 100;

    private final CreateFranchiseUseCase createFranchiseUseCase;
    private final AddBranchUseCase addBranchUseCase;
    private final AddBranchProductUseCase addBranchProductUseCase;
    private final DeleteBranchProductUseCase deleteBranchProductUseCase;
    private final UpdateBranchProductStockUseCase updateBranchProductStockUseCase;
    private final FindTopStockProductsUseCase findTopStockProductsUseCase;
    private final RenameFranchiseUseCase renameFranchiseUseCase;
    private final RenameBranchUseCase renameBranchUseCase;
    private final RenameBranchProductUseCase renameBranchProductUseCase;
    private final CursorCodec cursorCodec;

    public Mono<ServerResponse> createFranchise(ServerRequest request) {
        return requiredName(request)
                .flatMap(createFranchiseUseCase::execute)
                .flatMap(franchise -> ServerResponse.created(resourceUri("/api/v1/franchises/", franchise.getId()))
                        .eTag(versionTag(franchise.getVersion()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiMapper.toResponse(franchise)));
    }

    public Mono<ServerResponse> addBranch(ServerRequest request) {
        return Mono.zip(uuid(request, "franchiseId"), requiredName(request))
                .flatMap(input -> addBranchUseCase.execute(input.getT1(), input.getT2()))
                .flatMap(branch -> ServerResponse.created(resourceUri(
                                "/api/v1/franchises/" + branch.getFranchiseId() + "/branches/", branch.getId()))
                        .eTag(versionTag(branch.getVersion()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiMapper.toResponse(branch)));
    }

    public Mono<ServerResponse> addProduct(ServerRequest request) {
        return Mono.zip(
                        uuid(request, "franchiseId"),
                        uuid(request, "branchId"),
                        body(request, ApiRequests.CreateProduct.class).flatMap(this::requireProductName))
                .flatMap(input -> addBranchProductUseCase.execute(
                        input.getT1(), input.getT2(), input.getT3().name(), input.getT3().stock()))
                .flatMap(product -> ServerResponse.created(resourceUri(
                                "/api/v1/franchises/" + request.pathVariable("franchiseId")
                                        + "/branches/" + product.getBranchId() + "/products/",
                                product.getId()))
                        .eTag(versionTag(product.getVersion()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiMapper.toResponse(
                                product, UUID.fromString(request.pathVariable("franchiseId")))));
    }

    public Mono<ServerResponse> deleteProduct(ServerRequest request) {
        return Mono.zip(
                        uuid(request, "franchiseId"),
                        uuid(request, "branchId"),
                        uuid(request, "productId"),
                        RequestValues.ifMatch(request))
                .flatMap(input -> deleteBranchProductUseCase.execute(
                        input.getT1(), input.getT2(), input.getT3(), input.getT4()))
                .then(ServerResponse.noContent().build());
    }

    public Mono<ServerResponse> updateStock(ServerRequest request) {
        return Mono.zip(
                        uuid(request, "franchiseId"),
                        uuid(request, "branchId"),
                        uuid(request, "productId"),
                        requiredStock(request),
                        RequestValues.ifMatch(request))
                .flatMap(input -> updateBranchProductStockUseCase.execute(
                        input.getT1(), input.getT2(), input.getT3(), input.getT4(), input.getT5()))
                .flatMap(product -> ServerResponse.ok()
                        .eTag(versionTag(product.getVersion()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiMapper.toResponse(
                                product, UUID.fromString(request.pathVariable("franchiseId")))));
    }

    public Mono<ServerResponse> findTopStock(ServerRequest request) {
        return Mono.zip(
                        uuid(request, "franchiseId"),
                        cursorCodec.decode(request.queryParam("cursor")),
                        pageLimit(request))
                .flatMap(input -> findTopStockProductsUseCase.execute(
                                input.getT1(), input.getT2().orElse(null), input.getT3())
                        .map(page -> ApiMapper.toResponse(page, cursorCodec)))
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> renameFranchise(ServerRequest request) {
        return Mono.zip(uuid(request, "franchiseId"), requiredName(request), RequestValues.ifMatch(request))
                .flatMap(input -> renameFranchiseUseCase.execute(input.getT1(), input.getT2(), input.getT3()))
                .flatMap(franchise -> ServerResponse.ok()
                        .eTag(versionTag(franchise.getVersion()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiMapper.toResponse(franchise)));
    }

    public Mono<ServerResponse> renameBranch(ServerRequest request) {
        return Mono.zip(
                        uuid(request, "franchiseId"),
                        uuid(request, "branchId"),
                        requiredName(request),
                        RequestValues.ifMatch(request))
                .flatMap(input -> renameBranchUseCase.execute(
                        input.getT1(), input.getT2(), input.getT3(), input.getT4()))
                .flatMap(branch -> ServerResponse.ok()
                        .eTag(versionTag(branch.getVersion()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiMapper.toResponse(branch)));
    }

    public Mono<ServerResponse> renameProduct(ServerRequest request) {
        return Mono.zip(
                        uuid(request, "franchiseId"),
                        uuid(request, "branchId"),
                        uuid(request, "productId"),
                        requiredName(request),
                        RequestValues.ifMatch(request))
                .flatMap(input -> renameBranchProductUseCase.execute(
                        input.getT1(), input.getT2(), input.getT3(), input.getT4(), input.getT5()))
                .flatMap(product -> ServerResponse.ok()
                        .eTag(versionTag(product.getVersion()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiMapper.toResponse(
                                product, UUID.fromString(request.pathVariable("franchiseId")))));
    }

    private Mono<String> requiredName(ServerRequest request) {
        return body(request, ApiRequests.Name.class)
                .flatMap(input -> Mono.justOrEmpty(input.name()))
                .switchIfEmpty(Mono.error(new InvalidRequestException("name is required")));
    }

    private Mono<ApiRequests.CreateProduct> requireProductName(ApiRequests.CreateProduct request) {
        return Mono.just(request)
                .filter(input -> input.name() != null)
                .switchIfEmpty(Mono.error(new InvalidRequestException("name is required")));
    }

    private Mono<Integer> requiredStock(ServerRequest request) {
        return body(request, ApiRequests.Stock.class)
                .flatMap(input -> Mono.justOrEmpty(input.stock()))
                .switchIfEmpty(Mono.error(new InvalidRequestException("stock is required")));
    }

    private <T> Mono<T> body(ServerRequest request, Class<T> bodyType) {
        return Mono.justOrEmpty(request.headers().contentType())
                .filter(MediaType.APPLICATION_JSON::isCompatibleWith)
                .switchIfEmpty(Mono.error(new InvalidRequestException("Content-Type must be application/json")))
                .then(request.bodyToMono(bodyType))
                .switchIfEmpty(Mono.error(new InvalidRequestException("request body is required")));
    }

    private Mono<UUID> uuid(ServerRequest request, String name) {
        return Mono.fromCallable(() -> UUID.fromString(request.pathVariable(name)))
                .onErrorMap(error -> new InvalidRequestException(name + " must be a UUID"));
    }

    private Mono<Integer> pageLimit(ServerRequest request) {
        return Mono.fromCallable(() -> request.queryParam("limit")
                        .map(Integer::parseInt)
                        .orElse(DEFAULT_PAGE_LIMIT))
                .filter(limit -> limit >= 1 && limit <= MAXIMUM_PAGE_LIMIT)
                .switchIfEmpty(Mono.error(new InvalidRequestException(
                        "limit must be between 1 and " + MAXIMUM_PAGE_LIMIT)))
                .onErrorMap(NumberFormatException.class, error -> new InvalidRequestException("limit must be an integer"));
    }

    private URI resourceUri(String prefix, UUID id) {
        return URI.create(prefix + id);
    }

    private String versionTag(long version) {
        return '"' + Long.toString(version) + '"';
    }
}
