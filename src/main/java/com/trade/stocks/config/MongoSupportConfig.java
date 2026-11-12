package com.trade.stocks.config;

import com.trade.stocks.entity.AutoTradeRule;
import com.trade.stocks.entity.DatabaseSequence;
import com.trade.stocks.entity.PortfolioItem;
import com.trade.stocks.entity.Stock;
import com.trade.stocks.entity.Transaction;
import com.trade.stocks.entity.User;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * MongoDB persistence support active only when {@code app.db.resolved-mode=mongo}.
 * Provides atomic numeric ID sequences and a standalone-safe transaction manager for {@code @Transactional} services.
 */
@Configuration
@ConditionalOnProperty(name = "app.db.resolved-mode", havingValue = "mongo")
public class MongoSupportConfig {

    private static final List<SequenceTarget<?>> SEQUENCE_TARGETS = List.of(
            new SequenceTarget<>(User.class, "users_sequence", User::getId, User::setId),
            new SequenceTarget<>(Stock.class, "stocks_sequence", Stock::getId, Stock::setId),
            new SequenceTarget<>(PortfolioItem.class, "portfolio_items_sequence", PortfolioItem::getId, PortfolioItem::setId),
            new SequenceTarget<>(Transaction.class, "transactions_sequence", Transaction::getId, Transaction::setId),
            new SequenceTarget<>(AutoTradeRule.class, "auto_trade_rules_sequence", AutoTradeRule::getId, AutoTradeRule::setId)
    );

    @Bean
    public AbstractMongoEventListener<Object> mongoSequenceEventListener(MongoOperations mongoOperations) {
        return new AbstractMongoEventListener<>() {
            @Override
            public void onBeforeConvert(BeforeConvertEvent<Object> event) {
                assignIdIfMissing(event.getSource(), mongoOperations);
            }
        };
    }

    /**
     * Standalone MongoDB instances (such as Railway's default MongoDB service) run without a replica set
     * and reject multi-document transactions; a no-op transaction manager preserves {@code @Transactional} wiring.
     */
    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    public PlatformTransactionManager mongoCompatibleTransactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
                // No-op for standalone MongoDB deployments without replica-set sessions.
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                // Single-document MongoDB writes are atomic at commit time.
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                // No-op for standalone MongoDB deployments.
            }
        };
    }

    static void assignIdIfMissing(Object entity, MongoOperations mongoOperations) {
        if (entity == null) {
            return;
        }
        for (SequenceTarget<?> target : SEQUENCE_TARGETS) {
            if (target.assignIfMissing(entity, mongoOperations)) {
                return;
            }
        }
    }

    static long nextSequence(MongoOperations mongoOperations, String sequenceName) {
        DatabaseSequence counter = mongoOperations.findAndModify(
                Query.query(Criteria.where("_id").is(sequenceName)),
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                DatabaseSequence.class
        );
        return counter != null ? counter.getSeq() : 1L;
    }

    private record SequenceTarget<T>(
            Class<T> type,
            String sequenceName,
            Function<T, Long> idGetter,
            BiConsumer<T, Long> idSetter) {

        boolean assignIfMissing(Object entity, MongoOperations mongoOperations) {
            if (!type.isInstance(entity)) {
                return false;
            }
            T typed = type.cast(entity);
            if (idGetter.apply(typed) == null) {
                idSetter.accept(typed, nextSequence(mongoOperations, sequenceName));
            }
            return true;
        }
    }
}
