package co.com.pragma.api.dto;

import co.com.pragma.api.pagination.CursorCodec;
import co.com.pragma.model.branchproducts.BranchProduct;
import co.com.pragma.model.branchproducts.TopStockPage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ApiMapper {

    private ApiMapper() {
    }

    public static ApiResponses.Franchise toResponse(co.com.pragma.model.franchises.Franchise franchise) {
        return new ApiResponses.Franchise(
                franchise.getId(),
                franchise.getName(),
                franchise.getVersion(),
                franchise.getCreatedAt(),
                franchise.getUpdatedAt());
    }

    public static ApiResponses.Branch toResponse(co.com.pragma.model.branches.Branch branch) {
        return new ApiResponses.Branch(
                branch.getId(),
                branch.getFranchiseId(),
                branch.getName(),
                branch.getVersion(),
                branch.getCreatedAt(),
                branch.getUpdatedAt());
    }

    public static ApiResponses.Product toResponse(BranchProduct product, UUID franchiseId) {
        return new ApiResponses.Product(
                product.getId(),
                franchiseId,
                product.getBranchId(),
                product.getName(),
                product.getStock(),
                product.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    public static ApiResponses.TopStockPage toResponse(
            TopStockPage page, CursorCodec cursorCodec) {
        List<ApiResponses.BranchTopStock> items = new ArrayList<>(page.items().size());
        for (co.com.pragma.model.branchproducts.BranchTopStock item : page.items()) {
            items.add(new ApiResponses.BranchTopStock(
                    item.branchId(),
                    item.branchName(),
                    Optional.ofNullable(item.product())
                            .map(product -> new ApiResponses.TopStockProduct(
                                    product.getId(), product.getName(), product.getStock()))
                            .orElse(null)));
        }
        return new ApiResponses.TopStockPage(
                items,
                Optional.ofNullable(page.nextCursor())
                        .map(cursorCodec::encode)
                        .orElse(null));
    }
}
