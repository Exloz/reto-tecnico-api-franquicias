package co.com.pragma.config;

import co.com.pragma.model.branches.gateways.BranchRepository;
import co.com.pragma.model.branchproducts.gateways.BranchProductRepository;
import co.com.pragma.model.branchproducts.gateways.TopStockQueryRepository;
import co.com.pragma.model.franchises.gateways.FranchiseRepository;
import co.com.pragma.r2dbc.resilience.R2dbcResilience;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "adapters.r2dbc.host=localhost",
        "adapters.r2dbc.port=5432",
        "adapters.r2dbc.database=franchise",
        "adapters.r2dbc.schema=franchise",
        "adapters.r2dbc.username=franchise_app",
        "adapters.r2dbc.password=franchise_app",
        "adapters.r2dbc.ssl-mode=DISABLE",
        "adapters.r2dbc.initial-size=0",
        "adapters.r2dbc.max-size=2",
        "adapters.r2dbc.max-idle-time=1m"
})
class PersistenceContextTest {

    @Autowired
    private FranchiseRepository franchiseRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private BranchProductRepository branchProductRepository;

    @Autowired
    private TopStockQueryRepository topStockQueryRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Autowired
    private R2dbcResilience resilience;

    @Test
    void wiresPersistenceBeans() throws ClassNotFoundException {
        assertNotNull(franchiseRepository);
        assertNotNull(branchRepository);
        assertNotNull(branchProductRepository);
        assertNotNull(topStockQueryRepository);
        assertNotNull(resilience);
        assertTrue(applicationContext.getBeanNamesForType(
                Class.forName("org.springframework.r2dbc.core.DatabaseClient")).length > 0);
        assertTrue(applicationContext.getBeanNamesForType(
                Class.forName("org.springframework.transaction.reactive.TransactionalOperator")).length > 0);
        assertEquals("DISABLE", environment.getProperty("adapters.r2dbc.ssl-mode"));
        assertEquals("", environment.getProperty("adapters.r2dbc.ssl-root-cert"));
    }
}
