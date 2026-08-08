package co.com.pragma.usecase;

import co.com.pragma.model.branches.Branch;
import co.com.pragma.model.branches.gateways.BranchRepository;
import co.com.pragma.model.branchproducts.BranchProduct;
import co.com.pragma.model.branchproducts.gateways.BranchProductRepository;
import co.com.pragma.model.common.exception.DuplicateNameException;
import co.com.pragma.model.common.exception.VersionConflictException;
import co.com.pragma.model.franchises.Franchise;
import co.com.pragma.model.franchises.gateways.FranchiseRepository;
import co.com.pragma.usecase.renamebranch.RenameBranchUseCase;
import co.com.pragma.usecase.renamebranchproduct.RenameBranchProductUseCase;
import co.com.pragma.usecase.renamefranchise.RenameFranchiseUseCase;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenameUseCasesTest {
    private static final UUID FRANCHISE_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Mock
    private FranchiseRepository franchiseRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private BranchProductRepository productRepository;

    private RenameFranchiseUseCase renameFranchise;
    private RenameBranchUseCase renameBranch;
    private RenameBranchProductUseCase renameProduct;

    @BeforeEach
    void setUp() {
        renameFranchise = new RenameFranchiseUseCase(franchiseRepository);
        renameBranch = new RenameBranchUseCase(branchRepository);
        renameProduct = new RenameBranchProductUseCase(branchRepository, productRepository);
    }

    @Test
    void shouldTreatEquivalentFranchiseNameAsIdempotent() {
        Franchise current = franchise("Café Norte", "cafe norte", 2);
        when(franchiseRepository.findById(FRANCHISE_ID)).thenReturn(Mono.just(current));

        StepVerifier.create(renameFranchise.execute(FRANCHISE_ID, "  CAFÉ NORTE ", 2))
                .assertNext(result -> assertSame(current, result))
                .verifyComplete();

        verify(franchiseRepository, never()).rename(any(), any(Long.class));
    }

    @Test
    void shouldRenameFranchiseAndIncrementVersion() {
        when(franchiseRepository.findById(FRANCHISE_ID))
                .thenReturn(Mono.just(franchise("Old", "old", 2)));
        when(franchiseRepository.rename(any(Franchise.class), eq(2L)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Franchise.class)));

        StepVerifier.create(renameFranchise.execute(FRANCHISE_ID, "Núeva", 2))
                .assertNext(result -> {
                    assertEquals("Núeva", result.getName());
                    assertEquals("nueva", result.getNormalizedName());
                    assertEquals(3, result.getVersion());
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectStaleFranchiseVersion() {
        when(franchiseRepository.findById(FRANCHISE_ID))
                .thenReturn(Mono.just(franchise("Old", "old", 3)));

        StepVerifier.create(renameFranchise.execute(FRANCHISE_ID, "New", 2))
                .expectError(VersionConflictException.class)
                .verify();

        verify(franchiseRepository, never()).rename(any(), any(Long.class));
    }

    @Test
    void shouldPropagateDuplicateFranchiseRename() {
        when(franchiseRepository.findById(FRANCHISE_ID))
                .thenReturn(Mono.just(franchise("Old", "old", 2)));
        when(franchiseRepository.rename(any(Franchise.class), eq(2L)))
                .thenReturn(Mono.error(new DuplicateNameException("Franchise", "New")));

        StepVerifier.create(renameFranchise.execute(FRANCHISE_ID, "New", 2))
                .expectError(DuplicateNameException.class)
                .verify();
    }

    @Test
    void shouldTreatEquivalentBranchNameAsIdempotent() {
        Branch current = branch("Café", "cafe", 4);
        when(branchRepository.findByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID)).thenReturn(Mono.just(current));

        StepVerifier.create(renameBranch.execute(FRANCHISE_ID, BRANCH_ID, "CAFÉ", 4))
                .assertNext(result -> assertSame(current, result))
                .verifyComplete();

        verify(branchRepository, never()).rename(any(), any(Long.class));
    }

    @Test
    void shouldRenameBranchAndIncrementVersion() {
        when(branchRepository.findByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID))
                .thenReturn(Mono.just(branch("Old", "old", 4)));
        when(branchRepository.rename(any(Branch.class), eq(4L)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, Branch.class)));

        StepVerifier.create(renameBranch.execute(FRANCHISE_ID, BRANCH_ID, "Núeva", 4))
                .assertNext(result -> {
                    assertEquals("nueva", result.getNormalizedName());
                    assertEquals(5, result.getVersion());
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectStaleBranchVersion() {
        when(branchRepository.findByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID))
                .thenReturn(Mono.just(branch("Old", "old", 5)));

        StepVerifier.create(renameBranch.execute(FRANCHISE_ID, BRANCH_ID, "New", 4))
                .expectError(VersionConflictException.class)
                .verify();

        verify(branchRepository, never()).rename(any(), any(Long.class));
    }

    @Test
    void shouldPropagateDuplicateBranchRename() {
        when(branchRepository.findByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID))
                .thenReturn(Mono.just(branch("Old", "old", 4)));
        when(branchRepository.rename(any(Branch.class), eq(4L)))
                .thenReturn(Mono.error(new DuplicateNameException("Branch", "New")));

        StepVerifier.create(renameBranch.execute(FRANCHISE_ID, BRANCH_ID, "New", 4))
                .expectError(DuplicateNameException.class)
                .verify();
    }

    @Test
    void shouldTreatEquivalentProductNameAsIdempotent() {
        BranchProduct current = product("Café", "cafe", 6);
        when(branchRepository.findByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID))
                .thenReturn(Mono.just(branch("Branch", "branch", 0)));
        when(productRepository.findActiveByIdAndBranchId(PRODUCT_ID, BRANCH_ID)).thenReturn(Mono.just(current));

        StepVerifier.create(renameProduct.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID, "CAFÉ", 6))
                .assertNext(result -> assertSame(current, result))
                .verifyComplete();

        verify(productRepository, never()).rename(any(), any(Long.class));
    }

    @Test
    void shouldRenameProductAndIncrementVersion() {
        when(branchRepository.findByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID))
                .thenReturn(Mono.just(branch("Branch", "branch", 0)));
        when(productRepository.findActiveByIdAndBranchId(PRODUCT_ID, BRANCH_ID))
                .thenReturn(Mono.just(product("Old", "old", 6)));
        when(productRepository.rename(any(BranchProduct.class), eq(6L)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, BranchProduct.class)));

        StepVerifier.create(renameProduct.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID, "Núeva", 6))
                .assertNext(result -> {
                    assertEquals("nueva", result.getNormalizedName());
                    assertEquals(7, result.getVersion());
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectStaleProductVersion() {
        when(branchRepository.findByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID))
                .thenReturn(Mono.just(branch("Branch", "branch", 0)));
        when(productRepository.findActiveByIdAndBranchId(PRODUCT_ID, BRANCH_ID))
                .thenReturn(Mono.just(product("Old", "old", 7)));

        StepVerifier.create(renameProduct.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID, "New", 6))
                .expectError(VersionConflictException.class)
                .verify();

        verify(productRepository, never()).rename(any(), any(Long.class));
    }

    @Test
    void shouldPropagateDuplicateProductRename() {
        when(branchRepository.findByIdAndFranchiseId(BRANCH_ID, FRANCHISE_ID))
                .thenReturn(Mono.just(branch("Branch", "branch", 0)));
        when(productRepository.findActiveByIdAndBranchId(PRODUCT_ID, BRANCH_ID))
                .thenReturn(Mono.just(product("Old", "old", 6)));
        when(productRepository.rename(any(BranchProduct.class), eq(6L)))
                .thenReturn(Mono.error(new DuplicateNameException("Product", "New")));

        StepVerifier.create(renameProduct.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID, "New", 6))
                .expectError(DuplicateNameException.class)
                .verify();
    }

    private Franchise franchise(String name, String normalizedName, long version) {
        Instant now = Instant.now();
        return Franchise.builder()
                .id(FRANCHISE_ID)
                .name(name)
                .normalizedName(normalizedName)
                .version(version)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Branch branch(String name, String normalizedName, long version) {
        Instant now = Instant.now();
        return Branch.builder()
                .id(BRANCH_ID)
                .franchiseId(FRANCHISE_ID)
                .name(name)
                .normalizedName(normalizedName)
                .version(version)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private BranchProduct product(String name, String normalizedName, long version) {
        Instant now = Instant.now();
        return BranchProduct.builder()
                .id(PRODUCT_ID)
                .branchId(BRANCH_ID)
                .name(name)
                .normalizedName(normalizedName)
                .stock(10)
                .version(version)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
