package com.trade.stocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.trade.stocks.config.DatabaseModeEnvironmentPostProcessor;
import com.trade.stocks.config.MongoSupportConfig;
import com.trade.stocks.entity.AutoTradeRule;
import com.trade.stocks.entity.DatabaseSequence;
import com.trade.stocks.entity.PortfolioItem;
import com.trade.stocks.entity.Stock;
import com.trade.stocks.entity.Transaction;
import com.trade.stocks.entity.User;
import com.trade.stocks.repository.AutoTradeRuleRepository;
import com.trade.stocks.repository.PortfolioItemRepository;
import com.trade.stocks.repository.StockRepository;
import com.trade.stocks.repository.TransactionRepository;
import com.trade.stocks.repository.UserRepository;
import com.trade.stocks.service.TradeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

@SpringBootTest(classes = StocksApiApplication.class)
class DualDatabaseSupportTest {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TradeService tradeService;

    @Test
    void defaultModeBootsJpaWithH2AndExecutesTradeFlow() {
        assertThat(environment.getProperty(DatabaseModeEnvironmentPostProcessor.RESOLVED_MODE_PROPERTY))
                .isEqualTo(DatabaseModeEnvironmentPostProcessor.MODE_JPA);
        assertThat(environment.getProperty("spring.autoconfigure.exclude"))
                .contains("org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration")
                .contains("org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration")
                .contains("org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration");
        assertThat(applicationContext.getBeanNamesForType(MongoOperations.class)).isEmpty();

        User user = userRepository.findByUsername("nutTest1234").orElseThrow();
        List<Stock> stocks = stockRepository.findAll();
        assertThat(stocks).isNotEmpty();

        Stock affordableStock = stocks.stream()
                .filter(stock -> stock.getCurrentPrice().compareTo(user.getBalance()) <= 0)
                .findFirst()
                .orElseThrow();

        Transaction savedTx = tradeService.buyStock(user.getId(), affordableStock.getSymbol(), 1);
        assertThat(savedTx.getId()).isNotNull();
        assertThat(transactionRepository.findAllByOrderByTimestampDesc())
                .extracting(Transaction::getId)
                .contains(savedTx.getId());
    }

    @Test
    void postProcessorResolvesMongoModeFromRailwayMongoUrl() {
        MockEnvironment mockEnv = new MockEnvironment()
                .withProperty("app.db.type", "auto")
                .withProperty("MONGO_URL", "mongodb://mongo:secret@mongodb.railway.internal:27017");

        new DatabaseModeEnvironmentPostProcessor().postProcessEnvironment(mockEnv, new SpringApplication(StocksApiApplication.class));

        assertThat(mockEnv.getProperty(DatabaseModeEnvironmentPostProcessor.RESOLVED_MODE_PROPERTY))
                .isEqualTo(DatabaseModeEnvironmentPostProcessor.MODE_MONGO);
        assertThat(mockEnv.getProperty("spring.data.mongodb.uri"))
                .isEqualTo("mongodb://mongo:secret@mongodb.railway.internal:27017/stockdb?authSource=admin");
        assertThat(mockEnv.getProperty("spring.data.mongodb.database")).isEqualTo("stockdb");
        assertThat(mockEnv.getProperty("spring.h2.console.enabled")).isEqualTo("false");
        assertThat(mockEnv.getProperty("spring.autoconfigure.exclude"))
                .contains("org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
                .contains("org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration")
                .contains("org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration")
                .contains("org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration");
    }

    @Test
    void postProcessorHandlesMongoPrivateUrlCustomDatabaseAndQueryParams() {
        MockEnvironment privateUrlEnv = new MockEnvironment()
                .withProperty("MONGO_PRIVATE_URL", "mongodb://mongo:pw@h1:27017,h2:27017/?replicaSet=rs0")
                .withProperty("MONGO_DATABASE", "trade_prod");

        new DatabaseModeEnvironmentPostProcessor().postProcessEnvironment(privateUrlEnv, new SpringApplication(StocksApiApplication.class));

        assertThat(privateUrlEnv.getProperty("spring.data.mongodb.uri"))
                .isEqualTo("mongodb://mongo:pw@h1:27017,h2:27017/trade_prod?replicaSet=rs0&authSource=admin");
        assertThat(privateUrlEnv.getProperty("spring.data.mongodb.database")).isEqualTo("trade_prod");

        MockEnvironment explicitDbEnv = new MockEnvironment()
                .withProperty("MONGO_URL", "mongodb://user:pw@cluster.example.com:27017/customdb?retryWrites=true");

        new DatabaseModeEnvironmentPostProcessor().postProcessEnvironment(explicitDbEnv, new SpringApplication(StocksApiApplication.class));

        assertThat(explicitDbEnv.getProperty("spring.data.mongodb.uri"))
                .isEqualTo("mongodb://user:pw@cluster.example.com:27017/customdb?retryWrites=true");
        assertThat(explicitDbEnv.getProperty("spring.data.mongodb.database")).isEqualTo("customdb");
    }

    @Test
    void postProcessorConvertsRailwayPostgresDatabaseUrlToJdbc() {
        MockEnvironment mockEnv = new MockEnvironment()
                .withProperty("app.db.type", "auto")
                .withProperty("DATABASE_URL", "postgresql://pguser:p%40ss@postgres.railway.internal:5432/railway");

        new DatabaseModeEnvironmentPostProcessor().postProcessEnvironment(mockEnv, new SpringApplication(StocksApiApplication.class));

        assertThat(mockEnv.getProperty(DatabaseModeEnvironmentPostProcessor.RESOLVED_MODE_PROPERTY))
                .isEqualTo(DatabaseModeEnvironmentPostProcessor.MODE_JPA);
        assertThat(mockEnv.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://postgres.railway.internal:5432/railway");
        assertThat(mockEnv.getProperty("spring.datasource.username")).isEqualTo("pguser");
        assertThat(mockEnv.getProperty("spring.datasource.password")).isEqualTo("p@ss");
        assertThat(mockEnv.getProperty("spring.datasource.driverClassName"))
                .isEqualTo("org.postgresql.Driver");
        assertThat(mockEnv.getProperty("spring.jpa.database-platform"))
                .isEqualTo("org.hibernate.dialect.PostgreSQLDialect");
        assertThat(mockEnv.getProperty("spring.h2.console.enabled")).isEqualTo("false");
        assertThat(mockEnv.getProperty("spring.autoconfigure.exclude"))
                .contains("org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mongoSupportConfigWiresAndGeneratesNumericIdsAndTransactions() {
        MongoOperations mockMongoOps = mock(MongoOperations.class);
        DatabaseSequence sequence = new DatabaseSequence();
        sequence.setId("users_sequence");
        sequence.setSeq(101L);
        when(mockMongoOps.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(DatabaseSequence.class)
        )).thenReturn(sequence);

        ApplicationContextRunner mongoRunner = new ApplicationContextRunner()
                .withPropertyValues("app.db.resolved-mode=mongo")
                .withBean(MongoOperations.class, () -> mockMongoOps)
                .withUserConfiguration(MongoSupportConfig.class);

        mongoRunner.run(context -> {
            assertThat(context).hasSingleBean(MongoSupportConfig.class);
            assertThat(context).hasSingleBean(AbstractMongoEventListener.class);
            assertThat(context).hasSingleBean(PlatformTransactionManager.class);

            AbstractMongoEventListener<Object> listener = context.getBean(AbstractMongoEventListener.class);
            User user = new User();
            listener.onBeforeConvert(new BeforeConvertEvent<>(user, "users"));
            assertThat(user.getId()).isEqualTo(101L);

            PlatformTransactionManager txManager = context.getBean(PlatformTransactionManager.class);
            TransactionStatus txStatus = txManager.getTransaction(new DefaultTransactionDefinition());
            txManager.commit(txStatus);
        });

        ApplicationContextRunner jpaRunner = new ApplicationContextRunner()
                .withPropertyValues("app.db.resolved-mode=jpa")
                .withUserConfiguration(MongoSupportConfig.class);

        jpaRunner.run(context -> {
            assertThat(context).doesNotHaveBean(MongoSupportConfig.class);
            assertThat(context).doesNotHaveBean(AbstractMongoEventListener.class);
        });
    }

    @Test
    void mongoModeBootsAutoConfigurationAndRepositoriesWithoutJpaConflicts() {
        ApplicationContextRunner mongoAppRunner = new ApplicationContextRunner()
                .withPropertyValues(
                        "MONGO_URL=mongodb://mongo:secret@localhost:27017",
                        "spring.data.mongodb.auto-index-creation=false"
                )
                .withInitializer(context -> {
                    AutoConfigurationPackages.register((BeanDefinitionRegistry) context, "com.trade.stocks");
                    new DatabaseModeEnvironmentPostProcessor().postProcessEnvironment(
                            context.getEnvironment(),
                            new SpringApplication(StocksApiApplication.class)
                    );
                })
                .withUserConfiguration(MongoModeAutoConfigHarness.class);

        mongoAppRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MongoOperations.class);
            assertThat(context).hasSingleBean(MongoSupportConfig.class);
            assertThat(context).hasSingleBean(PlatformTransactionManager.class);
            assertThat(context).hasSingleBean(TradeService.class);
            assertThat(context).hasSingleBean(UserRepository.class);
            assertThat(context).hasSingleBean(StockRepository.class);
            assertThat(context).hasSingleBean(PortfolioItemRepository.class);
            assertThat(context).hasSingleBean(TransactionRepository.class);
            assertThat(context).hasSingleBean(AutoTradeRuleRepository.class);
            assertThat(context.getBeanNamesForType(javax.sql.DataSource.class)).isEmpty();

            MongoMappingContext mappingContext = context.getBean(MongoMappingContext.class);
            assertThat(mappingContext.getPersistentEntity(User.class).getIdProperty().getName()).isEqualTo("id");
            assertThat(mappingContext.getPersistentEntity(Stock.class).getIdProperty().getName()).isEqualTo("id");
            assertThat(mappingContext.getPersistentEntity(PortfolioItem.class).getIdProperty().getName()).isEqualTo("id");
            assertThat(mappingContext.getPersistentEntity(Transaction.class).getIdProperty().getName()).isEqualTo("id");
            assertThat(mappingContext.getPersistentEntity(AutoTradeRule.class).getIdProperty().getName()).isEqualTo("id");
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({MongoSupportConfig.class, TradeService.class})
    static class MongoModeAutoConfigHarness {
    }
}
