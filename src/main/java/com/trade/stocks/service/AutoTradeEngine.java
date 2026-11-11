package com.trade.stocks.service;

import com.trade.stocks.config.StockWebSocketHandler;
import com.trade.stocks.entity.AutoTradeRule;
import com.trade.stocks.entity.Stock;
import com.trade.stocks.entity.Transaction;
import com.trade.stocks.repository.AutoTradeRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AutoTradeEngine {

    private final AutoTradeRuleRepository autoTradeRepository;
    private final TradeService tradeService;
    private final StockWebSocketHandler webSocketHandler;

    public AutoTradeEngine(AutoTradeRuleRepository autoTradeRepository,
                            TradeService tradeService,
                            StockWebSocketHandler webSocketHandler) {
        this.autoTradeRepository = autoTradeRepository;
        this.tradeService = tradeService;
        this.webSocketHandler = webSocketHandler;
    }

    @Transactional
    public void evaluateRules(Stock stock) {
        List<AutoTradeRule> activeRules = autoTradeRepository.findByStatus("ACTIVE");
        
        for (AutoTradeRule rule : activeRules) {
            if (!rule.getSymbol().equalsIgnoreCase(stock.getSymbol())) {
                continue;
            }

            BigDecimal currentPrice = stock.getCurrentPrice();
            BigDecimal triggerPrice = rule.getTriggerPrice();
            boolean conditionMet = false;

            if ("BUY".equalsIgnoreCase(rule.getType())) {
                // Buy if current price falls to or below trigger price
                if (currentPrice.compareTo(triggerPrice) <= 0) {
                    conditionMet = true;
                }
            } else if ("SELL".equalsIgnoreCase(rule.getType())) {
                // Sell if current price rises to or above trigger price
                if (currentPrice.compareTo(triggerPrice) >= 0) {
                    conditionMet = true;
                }
            }

            if (conditionMet) {
                executeRule(rule, currentPrice);
            }
        }
    }

    private void executeRule(AutoTradeRule rule, BigDecimal executionPrice) {
        try {
            Transaction transaction;
            if ("BUY".equalsIgnoreCase(rule.getType())) {
                transaction = tradeService.buyStock(rule.getUserId(), rule.getSymbol(), rule.getQuantity());
            } else {
                transaction = tradeService.sellStock(rule.getUserId(), rule.getSymbol(), rule.getQuantity());
            }
            
            rule.setStatus("EXECUTED");
            autoTradeRepository.save(rule);
            
            // Broadcast success message to clients
            String notifyJson = String.format(
                "{\"type\":\"NOTIFICATION\",\"message\":\"Auto %s order for %d shares of %s executed successfully at $%s!\",\"userId\":%d}",
                rule.getType(), rule.getQuantity(), rule.getSymbol(), executionPrice, rule.getUserId()
            );
            webSocketHandler.broadcast(notifyJson);
            
        } catch (Exception e) {
            rule.setStatus("FAILED");
            autoTradeRepository.save(rule);
            
            String notifyJson = String.format(
                "{\"type\":\"NOTIFICATION\",\"message\":\"Auto %s order for %d shares of %s failed: %s\",\"userId\":%d}",
                rule.getType(), rule.getQuantity(), rule.getSymbol(), e.getMessage(), rule.getUserId()
            );
            webSocketHandler.broadcast(notifyJson);
        }
    }
}
