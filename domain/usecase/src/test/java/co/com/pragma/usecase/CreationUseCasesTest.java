package co.com.pragma.usecase;

import co.com.pragma.model.branches.Branch;
import co.com.pragma.model.branches.gateways.BranchRepository;
import co.com.pragma.model.branchproducts.BranchProduct;
import co.com.pragma.model.branchproducts.gateways.BranchProductRepository;
import co.com.pragma.model.common.exception.DuplicateNameException;
import co.com.pragma.model.common.exception.InvalidStockException;
import co.com.pragma.model.common.exception.ResourceNotFoundException;
import co.com.pragma.model.franchises.Franchise;
import co.com.pragma.model.franchises.gateways.FranchiseRepository;
import co.com.pragma.usecase.addbranch.AddBranchUseCase;
import co.com.pragma.usecase.addbranchproduct.AddBranchProductUseCase;
import co.com.pragma.usecase.createfranchise.CreateFranchiseUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreationUseCasesTest {
    private static final UUID FRANCHISE_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Mock
    private FranchiseRepository franchiseRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private BranchProductRepository productRepository;

    private CreateFranchiseUseCase createFranchise;
    private AddBranchUseCase addBranch;
    private AddBranchProductUseCase addProduct;

    @BeforeEach
    void setUp() {
        createFranchise = new CreateFranchiseUseCase(franchiseRepository);
        addBranch = new AddBranchUseCase(franchiseRepository, branchRepository);
        addProduct = new AddBranchProductUseCase(branchRepository, productRepository);
    }

    @Test
    void shouldCreateNormalizedFranchise() {
        when(franchiseRepository.create(any(Franchise.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Franchise.class)));

        StepVerifier.create(createFranchise.execute("  Franquícia Norte "))
                .assertNext(franchise -> {
                    assertNotNull(franchise.getId());
                    assertEquals("Franquícia Norte", franchise.getName());
                    assertEquals("franquicia norte", franchise.getNormalizedName());
                    assertEquals(0, franchise.getVersion());
                    assertNotNull(franchise.getCreatedAt());
                    assertEquals(franchise.getCreatedAt(), franchise.getUpdatedAt());
                })
                .verifyComplete();
    }

    @Test
    void shouldPropagateFranchiseDuplicateFromAtomicCreate() {
        when(franchiseRepository.create(any(Franchise.class)))
                .thenReturn(Mono.error(new DuplicateNameException("Franchise", "Norte")));

        StepVerifier.create(createFranchise.execute("Norte"))
                .expectError(DuplicateNameException.class)
                .verify();
    }

    @Test
    void shouldCreateBranchInsideExistingFranchise() {
        when(franchiseRepository.findById(FRANCHISE_ID)).thenReturn(Mono.just(franchise()));
        when(branchRepository.create(any(Branch.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Branch.class)));

        StepVerifier.create(addBranch.execute(FRANCHISE_ID, "  Sucursál Uno "))
                .assertNext(branch -> {
                    assertEquals(FRANCHISE_ID, branch.getFranchiseId());
                    assertEquals("Sucursál Uno", branch.getName());
                    assertEquals("sucursal uno", branch.getNormalizedName());
                    assertEquals(0, branch.getVersion());
                })
                .verifyComplete();
    }

    @Test
    void shouldNotCreateBranchWhenFranchiseDoesNotExist() {
        when(franchiseRepository.findById(FRANCHISE_ID)).thenReturn(Mono.empty());

        StepVerifier.create(addBranch.execute(FRANCHISE_ID, "Sucursal"))
                .expectError(ResourceNotFoundException.class)
                .verify();

        verify(branchRepository, never()).create(any());
    }

    @Test
    void shouldPropagateBranchDuplicateFromAtomicCreate() {
        when(franchiseRepository.findById(FRANCHISE_ID)).thenReturn(Mono.just(franchise()));
        when(branchRepository.create(any(Branch.class)))
                .thenReturn(Mono.error(new DuplicateNameException("Branch", "Sucursal")));

        StepVerifier.create(addBranch.execute(FRANCHISE_ID, "Sucursal"))
                .expectError(DuplicateNameException.class)
                .verify();
    }

    @Test
    void shouldCreateProductWithDefaultStock() {
        when(branchRepository.findByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID))
                .thenReturn(Mono.just(branch()));
        when(productRepository.create(any(BranchProduct.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, BranchProduct.class)));

        StepVerifier.create(addProduct.execute(FRANCHISE_ID, BRANCH_ID, "  Café Premium ", null))
                .assertNext(product -> {
                    assertEquals(BRANCH_ID, product.getBranchId());
                    assertEquals("Café Premium", product.getName());
                    assertEquals("cafe premium", product.getNormalizedName());
                    assertEquals(0, product.getStock());
                    assertEquals(0, product.getVersion());
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectNegativeInitialStockBeforeAccessingRepositories() {
        StepVerifier.create(addProduct.execute(FRANCHISE_ID, BRANCH_ID, "Producto", -1))
                .expectError(InvalidStockException.class)
                .verify();

        verifyNoInteractions(branchRepository, productRepository);
    }

    @Test
    void shouldNotCreateProductOutsideFranchiseHierarchy() {
        when(branchRepository.findByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.empty());

        StepVerifier.create(addProduct.execute(FRANCHISE_ID, BRANCH_ID, "Producto", 5))
                .expectError(ResourceNotFoundException.class)
                .verify();

        verify(productRepository, never()).create(any());
    }

    @Test
    void shouldPropagateProductDuplicateFromAtomicCreate() {
        when(branchRepository.findByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID))
                .thenReturn(Mono.just(branch()));
        when(productRepository.create(any(BranchProduct.class)))
                .thenReturn(Mono.error(new DuplicateNameException("Product", "Producto")));

        StepVerifier.create(addProduct.execute(FRANCHISE_ID, BRANCH_ID, "Producto", 5))
                .expectError(DuplicateNameException.class)
                .verify();
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
}
