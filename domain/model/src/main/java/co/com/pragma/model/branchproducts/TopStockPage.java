package co.com.pragma.model.branchproducts;

import java.util.List;

public record TopStockPage(List<BranchTopStock> items, TopStockCursor nextCursor) {

    public TopStockPage {
        items = List.copyOf(items);
    }
}
