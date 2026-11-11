package com.trade.stocks.service;

import com.trade.stocks.entity.*;
import com.trade.stocks.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TradeService {

    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final PortfolioItemRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final AutoTradeRuleRepository autoTradeRepository;

    public TradeService(UserRepository userRepository,
                        StockRepository stockRepository,
                        PortfolioItemRepository portfolioRepository,
                        TransactionRepository transactionRepository,
                        AutoTradeRuleRepository autoTradeRepository) {
        this.userRepository = userRepository;
        this.stockRepository = stockRepository;
        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.autoTradeRepository = autoTradeRepository;
    }

    @Transactional
    public Transaction buyStock(Long userId, String symbol, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Stock symbol not found"));

        BigDecimal price = stock.getCurrentPrice();
        BigDecimal totalCost = price.multiply(BigDecimal.valueOf(quantity));

        if (user.getBalance().compareTo(totalCost) < 0) {
            throw new IllegalStateException("Insufficient balance to execute buy");
        }

        // Deduct balance
        user.setBalance(user.getBalance().subtract(totalCost));
        userRepository.save(user);

        // Update portfolio
        Optional<PortfolioItem> itemOpt = portfolioRepository.findByUserIdAndSymbol(userId, symbol);
        if (itemOpt.isPresent()) {
            PortfolioItem item = itemOpt.get();
            int newQty = item.getQuantity() + quantity;
            
            // Calculate new average price
            BigDecimal currentTotal = item.getAveragePrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            BigDecimal newTotal = currentTotal.add(totalCost);
            BigDecimal newAvg = newTotal.divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP);
            
            item.setQuantity(newQty);
            item.setAveragePrice(newAvg);
            portfolioRepository.save(item);
        } else {
            PortfolioItem newItem = new PortfolioItem(null, userId, symbol, quantity, price);
            portfolioRepository.save(newItem);
        }

        // Record Transaction
        Transaction transaction = new Transaction(null, userId, symbol, "BUY", quantity, price, LocalDateTime.now());
        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction sellStock(Long userId, String symbol, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Stock symbol not found"));

        PortfolioItem item = portfolioRepository.findByUserIdAndSymbol(userId, symbol)
                .orElseThrow(() -> new IllegalStateException("You do not own this stock"));

        if (item.getQuantity() < quantity) {
            throw new IllegalStateException("Insufficient stock quantity to sell");
        }

        BigDecimal price = stock.getCurrentPrice();
        BigDecimal totalRevenue = price.multiply(BigDecimal.valueOf(quantity));

        // Add to balance
        user.setBalance(user.getBalance().add(totalRevenue));
        userRepository.save(user);

        // Update portfolio
        if (item.getQuantity() == quantity) {
            portfolioRepository.delete(item);
        } else {
            item.setQuantity(item.getQuantity() - quantity);
            portfolioRepository.save(item);
        }

        // Record Transaction
        Transaction transaction = new Transaction(null, userId, symbol, "SELL", quantity, price, LocalDateTime.now());
        return transactionRepository.save(transaction);
    }

    @Transactional
    public AutoTradeRule createAutoTradeRule(Long userId, String symbol, String type, BigDecimal triggerPrice, int quantity) {
        if (!type.equals("BUY") && !type.equals("SELL")) {
            throw new IllegalArgumentException("Type must be BUY or SELL");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (triggerPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Trigger price must be greater than zero");
        }

        stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Stock symbol not found"));

        AutoTradeRule rule = new AutoTradeRule(null, userId, symbol, type, triggerPrice, quantity, "ACTIVE");
        return autoTradeRepository.save(rule);
    }

    @Transactional
    public AutoTradeRule cancelAutoTradeRule(Long userId, Long ruleId) {
        AutoTradeRule rule = autoTradeRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));

        if (!rule.getUserId().equals(userId)) {
            throw new IllegalStateException("Unauthorized operation");
        }

        if (!rule.getStatus().equals("ACTIVE")) {
            throw new IllegalStateException("Only ACTIVE rules can be cancelled");
        }

        rule.setStatus("CANCELLED");
        return autoTradeRepository.save(rule);
    }

    public List<AutoTradeRule> getAutoTradeRules(Long userId) {
        return autoTradeRepository.findByUserId(userId);
    }

    public List<PortfolioItem> getPortfolio(Long userId) {
        return portfolioRepository.findByUserId(userId);
    }

    public List<Transaction> getTransactions(Long userId) {
        return transactionRepository.findByUserIdOrderByTimestampDesc(userId);
    }
}
