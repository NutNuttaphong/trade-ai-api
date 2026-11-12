package com.trade.stocks.repository;

import com.trade.stocks.entity.Transaction;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;

public interface TransactionRepository extends ListCrudRepository<Transaction, Long> {
    List<Transaction> findByUserIdOrderByTimestampDesc(Long userId);
    List<Transaction> findAllByOrderByTimestampDesc();
}
