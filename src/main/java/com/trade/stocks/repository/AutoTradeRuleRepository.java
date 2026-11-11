package com.trade.stocks.repository;

import com.trade.stocks.entity.AutoTradeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AutoTradeRuleRepository extends JpaRepository<AutoTradeRule, Long> {
    List<AutoTradeRule> findByUserId(Long userId);
    List<AutoTradeRule> findByStatus(String status);
}
