package co.com.pragma.usecase;

import co.com.pragma.model.branches.Branch;
import co.com.pragma.model.branches.gateways.BranchRepository;
import co.com.pragma.model.branchproducts.BranchProduct;
import co.com.pragma.model.branchproducts.BranchTopStock;
import co.com.pragma.model.branchproducts.gateways.BranchProductRepository;
import co.com.pragma.model.branchproducts.gateways.TopStockQueryRepository;
import co.com.pragma.model.common.exception.InvalidStockException;
import co.com.pragma.model.common.exception.ResourceNotFoundException;
import co.com.pragma.model.common.exception.VersionConflictException;
import co.com.pragma.model.franchises.Franchise;
import co.com.pragma.model.franchises.gateways.FranchiseRepository;
import co.com.pragma.usecase.deletebranchproduct.DeleteBranchProductUseCase;
import co.com.pragma.usecase.findtopstockproducts.FindTopStockProductsUseCase;
import co.com.pragma.usecase.updatebranchproductstock.UpdateBranchProductStockUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductOperationsUseCasesTest {
    private static final UUID FRANCHISE_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock
    private FranchiseRepository franchiseRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private BranchProductRepository productRepository;
    @Mock
    private TopStockQueryRepository topStockQueryRepository;

    private DeleteBranchProductUseCase deleteProduct;
    private UpdateBranchProductStockUseCase updateStock;
    private FindTopStockProductsUseCase findTopStock;

    @BeforeEach
    void setUp() {
        deleteProduct = new DeleteBranchProductUseCase(branchRepository, productRepository);
        updateStock = new UpdateBranchProductStockUseCase(branchRepository, productRepository);
        findTopStock = new FindTopStockProductsUseCase(franchiseRepository, topStockQueryRepository);
    }

    @Test
    void shouldSoftDeleteActiveProduct() {
        BranchProduct current = product(20, 3);
        when(branchRepository.findByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(branch()));
        when(productRepository.findActiveByIdAndBranchId(PRODUCT_ID, BRANCH_ID)).thenReturn(Mono.just(current));
        when(productRepository.softDelete(any(BranchProduct.class))).thenReturn(Mono.empty());

        StepVerifier.create(deleteProduct.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID))
                .verifyComplete();

        ArgumentCaptor<BranchProduct> captor = ArgumentCaptor.forClass(BranchProduct.class);
        verify(productRepository).softDelete(captor.capture());
        BranchProduct deleted = captor.getValue();
        assertEquals(4, deleted.getVersion());
        assertNotNull(deleted.getDeletedAt());
        assertEquals(deleted.getDeletedAt(), deleted.getUpdatedAt());
    }

    @Test
    void shouldFailDeleteWhenActiveProductDoesNotExist() {
        when(branchRepository.findByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(branch()));
        when(productRepository.findActiveByIdAndBranchId(PRODUCT_ID, BRANCH_ID)).thenReturn(Mono.empty());

        StepVerifier.create(deleteProduct.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID))
                .expectError(ResourceNotFoundException.class)
                .verify();

        verify(productRepository, never()).softDelete(any());
    }

    @Test
    void shouldReplaceStockAndIncrementVersion() {
        when(branchRepository.findByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(branch()));
        when(productRepository.findActiveByIdAndBranchId(PRODUCT_ID, BRANCH_ID))
                .thenReturn(Mono.just(product(20, 3)));
        when(productRepository.updateStock(any(BranchProduct.class), org.mockito.ArgumentMatchers.eq(3L)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, BranchProduct.class)));

        StepVerifier.create(updateStock.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID, 7, 3))
                .assertNext(updated -> {
                    assertEquals(7, updated.getStock());
                    assertEquals(4, updated.getVersion());
                    assertNull(updated.getDeletedAt());
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectStaleStockVersionWithoutWriting() {
        when(branchRepository.findByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(branch()));
        when(productRepository.findActiveByIdAndBranchId(PRODUCT_ID, BRANCH_ID))
                .thenReturn(Mono.just(product(20, 4)));

        StepVerifier.create(updateStock.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID, 7, 3))
                .expectError(VersionConflictException.class)
                .verify();

        verify(productRepository, never()).updateStock(any(), any(Long.class));
    }

    @Test
    void shouldRejectNegativeStockBeforeAccessingRepositories() {
        StepVerifier.create(updateStock.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID, -1, 3))
                .expectError(InvalidStockException.class)
                .verify();

        verifyNoInteractions(branchRepository, productRepository);
    }

    @Test
    void shouldValidateFranchiseBeforeReturningTopStock() {
        when(franchiseRepository.findById(FRANCHISE_ID)).thenReturn(Mono.empty());

        StepVerifier.create(findTopStock.execute(FRANCHISE_ID))
                .expectError(ResourceNotFoundException.class)
                .verify();

        verifyNoInteractions(topStockQueryRepository);
    }

    @Test
    void shouldReturnOrderedTopStockProjectionFromQueryPort() {
        BranchTopStock emptyBranch = new BranchTopStock(UUID.randomUUID(), "A Branch", null);
        BranchTopStock stockedBranch = new BranchTopStock(BRANCH_ID, "B Branch", product(9, 1));
        when(franchiseRepository.findById(FRANCHISE_ID)).thenReturn(Mono.just(franchise()));
        when(topStockQueryRepository.findTopActiveProductPerBranchOrdered(FRANCHISE_ID))
                .thenReturn(Flux.just(emptyBranch, stockedBranch));

        StepVerifier.create(findTopStock.execute(FRANCHISE_ID))
                .expectNext(emptyBranch, stockedBranch)
                .verifyComplete();
    }

    private Franchise franchise() {
        Instant now = Instant.now();
        return Franchise.builder()
                .id(FRANCHISE_ID)
                .name("Franchise")
                .normalizedName("franchise")
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Branch branch() {
        Instant now = Instant.now();
        return Branch.builder()
                .id(BRANCH_ID)
                .franchiseId(FRANCHISE_ID)
                .name("Branch")
                .normalizedName("branch")
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private BranchProduct product(int stock, long version) {
        Instant now = Instant.now();
        return BranchProduct.builder()
                .id(PRODUCT_ID)
                .branchId(BRANCH_ID)
                .name("Product")
                .normalizedName("product")
                .stock(stock)
                .version(version)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
