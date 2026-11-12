package com.trade.stocks.repository;

import com.trade.stocks.entity.PortfolioItem;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;

public interface PortfolioItemRepository extends ListCrudRepository<PortfolioItem, Long> {
    List<PortfolioItem> findByUserId(Long userId);
    Optional<PortfolioItem> findByUserIdAndSymbol(Long userId, String symbol);
}
