package com.trade.stocks.controller;

import com.trade.stocks.entity.AutoTradeRule;
import com.trade.stocks.entity.PortfolioItem;
import com.trade.stocks.entity.Transaction;
import com.trade.stocks.entity.User;
import com.trade.stocks.service.TradeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trade")
public class TradeController {

    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    private User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @PostMapping("/buy")
    public ResponseEntity<?> buyStock(@RequestBody TradeRequest request) {
        User user = getAuthenticatedUser();
        try {
            Transaction tx = tradeService.buyStock(user.getId(), request.getSymbol(), request.getQuantity());
            return ResponseEntity.ok(tx);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/sell")
    public ResponseEntity<?> sellStock(@RequestBody TradeRequest request) {
        User user = getAuthenticatedUser();
        try {
            Transaction tx = tradeService.sellStock(user.getId(), request.getSymbol(), request.getQuantity());
            return ResponseEntity.ok(tx);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/portfolio")
    public List<PortfolioItem> getPortfolio() {
        User user = getAuthenticatedUser();
        return tradeService.getPortfolio(user.getId());
    }

    @GetMapping("/transactions")
    public List<Transaction> getTransactions() {
        User user = getAuthenticatedUser();
        return tradeService.getTransactions(user.getId());
    }

    @GetMapping("/auto-rules")
    public List<AutoTradeRule> getAutoTradeRules() {
        User user = getAuthenticatedUser();
        return tradeService.getAutoTradeRules(user.getId());
    }

    @PostMapping("/auto-rules")
    public ResponseEntity<?> createAutoRule(@RequestBody AutoTradeRequest request) {
        User user = getAuthenticatedUser();
        try {
            AutoTradeRule rule = tradeService.createAutoTradeRule(
                    user.getId(),
                    request.getSymbol(),
                    request.getType(),
                    request.getTriggerPrice(),
                    request.getQuantity()
            );
            return ResponseEntity.ok(rule);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/auto-rules/{ruleId}")
    public ResponseEntity<?> cancelAutoRule(@PathVariable Long ruleId) {
        User user = getAuthenticatedUser();
        try {
            AutoTradeRule rule = tradeService.cancelAutoTradeRule(user.getId(), ruleId);
            return ResponseEntity.ok(rule);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    public static class TradeRequest {
        private String symbol;
        private int quantity;

        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }

    public static class AutoTradeRequest {
        private String symbol;
        private String type; // BUY or SELL
        private BigDecimal triggerPrice;
        private int quantity;

        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public BigDecimal getTriggerPrice() { return triggerPrice; }
        public void setTriggerPrice(BigDecimal triggerPrice) { this.triggerPrice = triggerPrice; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}
