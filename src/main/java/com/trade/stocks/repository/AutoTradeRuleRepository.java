package com.trade.stocks.repository;

import com.trade.stocks.entity.AutoTradeRule;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;

public interface AutoTradeRuleRepository extends ListCrudRepository<AutoTradeRule, Long> {
    List<AutoTradeRule> findByUserId(Long userId);
    List<AutoTradeRule> findByStatus(String status);
}
