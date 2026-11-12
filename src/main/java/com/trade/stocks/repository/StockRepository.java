package com.trade.stocks.repository;

import com.trade.stocks.entity.Stock;
import org.springframework.data.repository.ListCrudRepository;
import java.util.Optional;

public interface StockRepository extends ListCrudRepository<Stock, Long> {
    Optional<Stock> findBySymbol(String symbol);
}
